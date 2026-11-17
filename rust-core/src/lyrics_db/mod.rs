use encoding_rs::{GB18030, UTF_16LE, UTF_8};
use pinyin::ToPinyin;
use rusqlite::{params, Connection};

#[derive(Debug, Clone)]
pub struct LyricSearchHit {
    pub song_id: String,
    pub title: String,
    pub artist: String,
    pub time_ms: i64,
    pub lyric_line: String,
}

pub struct LyricsDatabase {
    conn: Connection,
}

impl LyricsDatabase {
    pub fn open(db_path: &str) -> Result<Self, String> {
        let conn = Connection::open(db_path).map_err(|e| e.to_string())?;
        conn.execute_batch(
            r#"
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;

            CREATE TABLE IF NOT EXISTS tracks_lru (
                song_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                nas_url TEXT NOT NULL,
                local_audio_path TEXT,
                cached_bytes INTEGER DEFAULT 0,
                last_played_at INTEGER NOT NULL,
                last_position_ms INTEGER DEFAULT 0,
                is_pinned INTEGER DEFAULT 0
            );

            CREATE VIRTUAL TABLE IF NOT EXISTS lyrics_fts USING fts5(
                song_id UNINDEXED,
                time_ms UNINDEXED,
                title,
                artist,
                lyric_line,
                lyric_pinyin,
                tokenize = 'unicode61'
            );
            "#,
        )
        .map_err(|e| e.to_string())?;

        Ok(Self { conn })
    }

    /// 自动识别 UTF-8 / UTF-16LE / GBK(GB18030) 编码的经典老歌 .lrc 字节流，解析时间戳并生成汉字+无声调拼音双索引
    pub fn ingest_lrc_bytes(
        &mut self,
        song_id: &str,
        title: &str,
        artist: &str,
        raw_bytes: &[u8],
    ) -> Result<usize, String> {
        let decoded = Self::decode_lrc_auto_charset(raw_bytes);
        let tx = self.conn.transaction().map_err(|e| e.to_string())?;

        tx.execute("DELETE FROM lyrics_fts WHERE song_id = ?1", params![song_id])
            .map_err(|e| e.to_string())?;

        let mut inserted = 0usize;
        for raw_line in decoded.lines() {
            if let Some((time_ms, text)) = Self::parse_lrc_timestamp_line(raw_line) {
                if text.is_empty() {
                    continue;
                }
                let py = Self::to_toneless_pinyin(&text);
                tx.execute(
                    "INSERT INTO lyrics_fts (song_id, time_ms, title, artist, lyric_line, lyric_pinyin)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                    params![song_id, time_ms, title, artist, text, py],
                )
                .map_err(|e| e.to_string())?;
                inserted += 1;
            }
        }

        tx.commit().map_err(|e| e.to_string())?;
        Ok(inserted)
    }

    /// 车载语音搜歌/搜词核心：先匹配汉字全文，若因胎噪出现同音错别字则自动回退到无声调拼音匹配
    pub fn search_by_hanzi_or_pinyin(
        &self,
        spoken_query: &str,
    ) -> Result<Option<LyricSearchHit>, String> {
        let clean = spoken_query.trim().replace('"', "");
        if clean.is_empty() {
            return Ok(None);
        }

        // 1. 优先精确搜索汉字（匹配歌名、歌手或单句歌词）
        let fts_hanzi = format!(""{}"", clean);
        if let Some(hit) = self.run_fts_query(&fts_hanzi)? {
            return Ok(Some(hit));
        }

        // 2. 自动转无声调拼音进行同音容错匹配（如语音把“周洁伦”识别错，拼音依然是 zhou jie lun）
        let py_query = format!(""{}"", Self::to_toneless_pinyin(&clean));
        self.run_fts_query(&py_query)
    }

    /// 根据当前播放毫秒数查询当前应当高亮显示的 LRC 歌词行
    pub fn get_active_lyric_line(&self, song_id: &str, pos_ms: i64) -> Option<String> {
        self.conn
            .query_row(
                "SELECT lyric_line FROM lyrics_fts
                 WHERE song_id = ?1 AND CAST(time_ms AS INTEGER) <= ?2
                 ORDER BY CAST(time_ms AS INTEGER) DESC LIMIT 1",
                params![song_id, pos_ms],
                |row| row.get(0),
            )
            .ok()
    }

    fn run_fts_query(&self, match_expr: &str) -> Result<Option<LyricSearchHit>, String> {
        let mut stmt = self
            .conn
            .prepare(
                "SELECT song_id, title, artist, CAST(time_ms AS INTEGER), lyric_line
                 FROM lyrics_fts
                 WHERE lyrics_fts MATCH ?1
                 ORDER BY rank LIMIT 1",
            )
            .map_err(|e| e.to_string())?;

        let mut rows = stmt
            .query_map(params![match_expr], |row| {
                Ok(LyricSearchHit {
                    song_id: row.get(0)?,
                    title: row.get(1)?,
                    artist: row.get(2)?,
                    time_ms: row.get(3)?,
                    lyric_line: row.get(4)?,
                })
            })
            .map_err(|e| e.to_string())?;

        if let Some(first) = rows.next() {
            return first.map(Some).map_err(|e| e.to_string());
        }
        Ok(None)
    }

    /// 自动探测 UTF-8 BOM / UTF-16LE / 纯 UTF-8 / GB18030(GBK)
    fn decode_lrc_auto_charset(bytes: &[u8]) -> String {
        if bytes.starts_with(&[0xFF, 0xFE]) {
            let (cow, _, _) = UTF_16LE.decode(&bytes[2..]);
            return cow.into_owned();
        }
        if let Ok(utf8_str) = std::str::from_utf8(bytes) {
            return utf8_str.to_string();
        }
        let (cow, _, _) = GB18030.decode(bytes);
        cow.into_owned()
    }

    /// 解析形如 `[01:23.45]刮风这天 我试过握着你手` 的 LRC 时间标签
    fn parse_lrc_timestamp_line(line: &str) -> Option<(i64, String)> {
        let trimmed = line.trim();
        if !trimmed.starts_with('[') {
            return None;
        }
        let close_idx = trimmed.find(']')?;
        let tag = &trimmed[1..close_idx];
        let content = trimmed[close_idx + 1..].trim().to_string();

        let mut parts = tag.split(':');
        let minutes: i64 = parts.next()?.parse().ok()?;
        let sec_str = parts.next()?;
        let seconds: f64 = sec_str.parse().ok()?;
        let total_ms = minutes * 60_000 + (seconds * 1000.0) as i64;
        Some((total_ms, content))
    }

    /// 将中文字符串转为以空格分隔的无声调全拼（如 "最美的不是下雨天" -> "zui mei de bu shi xia yu tian"）
    fn to_toneless_pinyin(text: &str) -> String {
        let mut out = Vec::new();
        for ch in text.chars() {
            if let Some(py) = ch.to_pinyin() {
                out.push(py.plain().to_string());
            } else if ch.is_alphanumeric() {
                out.push(ch.to_lowercase().to_string());
            }
        }
        out.join(" ")
    }
}
