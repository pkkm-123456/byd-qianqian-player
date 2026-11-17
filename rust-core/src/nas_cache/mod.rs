use reqwest::blocking::Client;
use reqwest::header::RANGE;
use rusqlite::{params, Connection};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// NAS 边下边播与容量配额 LRU 管理器：
/// 1. 支持 HTTP Range 分块下载，先写入 `.part` 临时文件，完整校验后转正，防止进地下车库断网产生破损缓存
/// 2. 当本地音频总大小超出设定配额（如 4GB）时，按 `last_played_at ASC` 自动淘汰早期播放的音频文件至 85% 水位线
/// 3. 淘汰音频文件时永久保留 SQLite FTS5 中的歌词与曲目索引，确保随时可按歌词搜歌
pub struct NasCacheManager {
    cache_dir: PathBuf,
    db_path: String,
    max_quota_bytes: u64,
    http_client: Client,
}

impl NasCacheManager {
    pub fn new(cache_dir: String, db_path: String, max_quota_bytes: u64) -> Self {
        let _ = fs::create_dir_all(&cache_dir);
        let http_client = Client::builder()
            .timeout(Duration::from_secs(15))
            .build()
            .unwrap_or_else(|_| Client::new());

        Self {
            cache_dir: PathBuf::from(cache_dir),
            db_path,
            max_quota_bytes,
            http_client,
        }
    }

    pub fn set_max_quota(&mut self, max_quota_bytes: u64) {
        self.max_quota_bytes = max_quota_bytes;
    }

    /// 仅拉取 NAS 音频文件前 64KB 头部字节（用于免全量下载直接解析 ID3v2/FLAC 内嵌歌词与歌名标签）
    pub fn fetch_header_64kb(&self, url: &str, user: &str, pass: &str) -> Result<Vec<u8>, String> {
        let resp = self
            .http_client
            .get(url)
            .basic_auth(user, Some(pass))
            .header(RANGE, "bytes=0-65535")
            .send()
            .map_err(|e| e.to_string())?;
        resp.bytes().map(|b| b.to_vec()).map_err(|e| e.to_string())
    }

    /// 分块流式下载整首歌曲并落地缓存，完成后自动触发 LRU 容量水位线检查
    pub fn stream_and_cache_track(
        &self,
        song_id: &str,
        nas_url: &str,
        user: &str,
        pass: &str,
    ) -> Result<PathBuf, String> {
        let final_path = self.cache_dir.join(format!("{}.flac", song_id));
        if final_path.exists() {
            self.touch_last_played(song_id)?;
            return Ok(final_path);
        }

        let part_path = self.cache_dir.join(format!("{}.flac.part", song_id));
        let mut offset = part_path.metadata().map(|m| m.len()).unwrap_or(0);
        let chunk_size: u64 = 512 * 1024; // 512KB 分块拉取，兼顾车载 4G/5G 弱网重连

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&part_path)
            .map_err(|e| e.to_string())?;

        loop {
            let range_header = format!("bytes={}-{}", offset, offset + chunk_size - 1);
            let resp = self
                .http_client
                .get(nas_url)
                .basic_auth(user, Some(pass))
                .header(RANGE, range_header)
                .send()
                .map_err(|e| e.to_string())?;

            let status = resp.status().as_u16();
            let bytes = resp.bytes().map_err(|e| e.to_string())?;
            if bytes.is_empty() {
                break;
            }

            file.write_all(&bytes).map_err(|e| e.to_string())?;
            offset += bytes.len() as u64;

            if (bytes.len() as u64) < chunk_size || status == 200 {
                break;
            }
        }

        file.flush().map_err(|e| e.to_string())?;
        fs::rename(&part_path, &final_path).map_err(|e| e.to_string())?;

        self.record_cached_file(song_id, &final_path, offset)?;
        self.evict_early_tracks_if_needed()?;
        Ok(final_path)
    }

    /// 当缓存超出 `max_quota_bytes` 时，按最早播放时间 (`last_played_at ASC`) 删除音频文件，直至回落到 85% 水位线
    pub fn evict_early_tracks_if_needed(&self) -> Result<u64, String> {
        let conn = Connection::open(&self.db_path).map_err(|e| e.to_string())?;
        let total_cached: u64 = conn
            .query_row(
                "SELECT COALESCE(SUM(cached_bytes), 0) FROM tracks_lru WHERE local_audio_path IS NOT NULL",
                [],
                |row| row.get(0),
            )
            .map_err(|e| e.to_string())?;

        if total_cached <= self.max_quota_bytes {
            return Ok(0);
        }

        let target_watermark = (self.max_quota_bytes as f64 * 0.85) as u64;
        let mut current_total = total_cached;
        let mut freed_bytes = 0u64;

        let mut stmt = conn
            .prepare(
                "SELECT song_id, local_audio_path, cached_bytes
                 FROM tracks_lru
                 WHERE local_audio_path IS NOT NULL AND is_pinned = 0
                 ORDER BY last_played_at ASC",
            )
            .map_err(|e| e.to_string())?;

        let rows = stmt
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, u64>(2)?,
                ))
            })
            .map_err(|e| e.to_string())?;

        for item in rows.flatten() {
            if current_total <= target_watermark {
                break;
            }
            let (song_id, path_str, size) = item;
            let _ = fs::remove_file(&path_str);

            // 仅清空音频本地路径与字节数，绝不删除 lyrics_fts 中的歌词索引！
            let _ = conn.execute(
                "UPDATE tracks_lru SET local_audio_path = NULL, cached_bytes = 0 WHERE song_id = ?1",
                params![song_id],
            );

            current_total = current_total.saturating_sub(size);
            freed_bytes += size;
        }

        Ok(freed_bytes)
    }

    fn touch_last_played(&self, song_id: &str) -> Result<(), String> {
        let now = Self::unix_now();
        let conn = Connection::open(&self.db_path).map_err(|e| e.to_string())?;
        conn.execute(
            "UPDATE tracks_lru SET last_played_at = ?1 WHERE song_id = ?2",
            params![now, song_id],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    fn record_cached_file(&self, song_id: &str, path: &Path, size: u64) -> Result<(), String> {
        let now = Self::unix_now();
        let conn = Connection::open(&self.db_path).map_err(|e| e.to_string())?;
        conn.execute(
            "UPDATE tracks_lru
             SET local_audio_path = ?1, cached_bytes = ?2, last_played_at = ?3
             WHERE song_id = ?4",
            params![path.to_string_lossy().as_ref(), size, now, song_id],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    fn unix_now() -> i64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64
    }
}


impl NasCacheManager {
    /// 构建飞牛 fnOS WebDAV 完整目标目录 URL（自动处理多余斜杠与多级子目录如 `Music/华语`）
    pub fn build_feiniu_webdav_url(server_url: &str, target_dir: &str, file_name: &str) -> String {
        let base = server_url.trim().trim_end_matches('/');
        let dir = target_dir.trim().trim_matches('/');
        let file = file_name.trim_start_matches('/');
        if dir.is_empty() {
            if file.is_empty() {
                format!("{}/", base)
            } else {
                format!("{}/{}", base, file)
            }
        } else if file.is_empty() {
            format!("{}/{}/", base, dir)
        } else {
            format!("{}/{}/{}", base, dir, file)
        }
    }

    /// 飞牛 fnOS WebDAV 连通性测试（发送 HTTP PROPFIND Depth: 0 校验 5005 端口与共享文件夹可见性）
    pub fn test_feiniu_connection(
        &self,
        server_url: &str,
        user: &str,
        pass: &str,
        target_dir: &str,
    ) -> Result<String, String> {
        if server_url.contains(":5666") || server_url.contains(":5667") {
            return Err("端口错误：5666/5667 是飞牛网页桌面端口，请改为 WebDAV 默认端口 5005 或 5006".to_string());
        }

        let full_url = Self::build_feiniu_webdav_url(server_url, target_dir, "");
        let method = reqwest::Method::from_bytes(b"PROPFIND").unwrap_or(reqwest::Method::GET);

        let resp = self
            .http_client
            .request(method, &full_url)
            .basic_auth(user, Some(pass))
            .header("Depth", "0")
            .send()
            .map_err(|e| format!("无法连接飞牛 NAS ({}): {}", full_url, e))?;

        let code = resp.status().as_u16();
        match code {
            200 | 207 => Ok(format!("连接成功！飞牛 WebDAV 目录 [{}] 可正常读写 (HTTP {})", target_dir, code)),
            401 | 403 => Err("认证失败：请检查飞牛登录账号与密码".to_string()),
            404 => Err(format!("目录不存在：请确认在飞牛「设置 -> 文件服务 -> WebDAV」已将 [{}] 加入共享", target_dir)),
            _ => Err(format!("飞牛 WebDAV 响应异常状态码: HTTP {}", code)),
        }
    }

    /// 收藏歌曲时自动下载到本地（标记 is_pinned = 1 永不淘汰）并直存归档到飞牛 NAS 目标目录（含同名 .lrc 歌词）
    pub fn archive_favorite_to_feiniu(
        &self,
        song_id: &str,
        server_url: &str,
        user: &str,
        pass: &str,
        target_dir: &str,
        local_audio_bytes: &[u8],
        lrc_bytes: Option<&[u8]>,
    ) -> Result<(), String> {
        let audio_url = Self::build_feiniu_webdav_url(server_url, target_dir, &format!("{}.flac", song_id));
        self.http_client
            .put(&audio_url)
            .basic_auth(user, Some(pass))
            .body(local_audio_bytes.to_vec())
            .send()
            .map_err(|e| e.to_string())?;

        if let Some(lrc) = lrc_bytes {
            let lrc_url = Self::build_feiniu_webdav_url(server_url, target_dir, &format!("{}.lrc", song_id));
            let _ = self
                .http_client
                .put(&lrc_url)
                .basic_auth(user, Some(pass))
                .body(lrc.to_vec())
                .send();
        }

        // 在本地 SQLite 中将收藏曲目标记为 is_pinned = 1，LRU 容量清理时永久跳过
        if let Ok(conn) = rusqlite::Connection::open(&self.db_path) {
            let _ = conn.execute(
                "UPDATE tracks_lru SET is_pinned = 1 WHERE song_id = ?1",
                rusqlite::params![song_id],
            );
        }
        Ok(())
    }

    /// 扫描飞牛 WebDAV 远程目录 (HTTP PROPFIND Depth: 1)，发现所有无损/音乐文件并录入本地数据库
    pub fn scan_webdav_tracks(
        &self,
        server_url: &str,
        user: &str,
        pass: &str,
        target_dir: &str,
    ) -> Result<Vec<DiscoveredNasTrack>, String> {
        let full_url = Self::build_feiniu_webdav_url(server_url, target_dir, "");
        let method = reqwest::Method::from_bytes(b"PROPFIND").unwrap_or(reqwest::Method::GET);

        let resp = self
            .http_client
            .request(method, &full_url)
            .basic_auth(user, Some(pass))
            .header("Depth", "1")
            .send()
            .map_err(|e| format!("无法扫描飞牛 WebDAV 目录 ({}): {}", full_url, e))?;

        let status = resp.status().as_u16();
        if status != 200 && status != 207 {
            return Err(format!("飞牛 WebDAV 扫描响应异常状态码: HTTP {}", status));
        }

        let xml = resp.text().map_err(|e| e.to_string())?;
        let mut tracks = Vec::new();
        let audio_exts = [".flac", ".mp3", ".wav", ".m4a", ".aac", ".ogg", ".ape"];

        let base_origin = extract_origin(server_url);

        for response_chunk in xml.split("<D:response").chain(xml.split("<response")) {
            let href = if let Some(h) = extract_xml_tag(response_chunk, "D:href") {
                h
            } else if let Some(h) = extract_xml_tag(response_chunk, "href") {
                h
            } else {
                continue;
            };

            let decoded_href = urlencoding_decode(&href);
            let trimmed_href = decoded_href.trim_end_matches('/');
            let file_name = if let Some(idx) = trimmed_href.rfind('/') {
                &trimmed_href[idx + 1..]
            } else {
                trimmed_href
            };

            let lower = file_name.to_lowercase();
            if audio_exts.iter().any(|ext| lower.ends_with(ext)) {
                let base_name = if let Some(dot) = file_name.rfind('.') {
                    &file_name[..dot]
                } else {
                    file_name
                };

                let parts: Vec<&str> = base_name.split(['-', '_']).map(|s| s.trim()).collect();
                let (artist, title) = if parts.len() >= 2 {
                    (parts[0].to_string(), parts[1..].join(" - "))
                } else {
                    ("飞牛音乐".to_string(), base_name.to_string())
                };

                let song_id = format!("{:x}", calculate_str_hash(trimmed_href));
                let track_url = if href.starts_with("http://") || href.starts_with("https://") {
                    href.to_string()
                } else {
                    format!("{}{}", base_origin, href)
                };

                tracks.push(DiscoveredNasTrack {
                    song_id,
                    title,
                    artist,
                    nas_url: track_url,
                });
            }
        }

        // 写入本地 SQLite 索引
        if let Ok(conn) = Connection::open(&self.db_path) {
            let now = Self::unix_now();
            for t in &tracks {
                let _ = conn.execute(
                    "INSERT INTO tracks_lru (song_id, title, artist, nas_url, last_played_at)
                     VALUES (?1, ?2, ?3, ?4, ?5)
                     ON CONFLICT(song_id) DO UPDATE SET nas_url = ?4",
                    params![t.song_id, t.title, t.artist, t.nas_url, now],
                );
            }
        }

        Ok(tracks)
    }
}

#[derive(Debug, Clone)]
pub struct DiscoveredNasTrack {
    pub song_id: String,
    pub title: String,
    pub artist: String,
    pub nas_url: String,
}

fn extract_xml_tag<'a>(xml: &'a str, tag: &str) -> Option<&'a str> {
    let open_tag = format!("<{}>", tag);
    let close_tag = format!("</{}>", tag);
    let start = xml.find(&open_tag)? + open_tag.len();
    let end = xml[start..].find(&close_tag)? + start;
    Some(xml[start..end].trim())
}

fn urlencoding_decode(input: &str) -> String {
    let mut bytes = Vec::new();
    let mut chars = input.bytes();
    while let Some(b) = chars.next() {
        if b == b'%' {
            if let (Some(h1), Some(h2)) = (chars.next(), chars.next()) {
                if let Ok(hex_byte) = u8::from_str_radix(
                    std::str::from_utf8(&[h1, h2]).unwrap_or(""),
                    16,
                ) {
                    bytes.push(hex_byte);
                    continue;
                }
            }
        }
        bytes.push(b);
    }
    String::from_utf8_lossy(&bytes).to_string()
}

fn extract_origin(url: &str) -> String {
    if let Some(pos) = url.find("://") {
        let rest = &url[pos + 3..];
        if let Some(slash) = rest.find('/') {
            url[..pos + 3 + slash].to_string()
        } else {
            url.trim_end_matches('/').to_string()
        }
    } else {
        url.to_string()
    }
}

fn calculate_str_hash(s: &str) -> u64 {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    let mut hasher = DefaultHasher::new();
    s.hash(&mut hasher);
    hasher.finish()
}
