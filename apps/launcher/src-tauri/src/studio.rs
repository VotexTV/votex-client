use crate::storage::{self, Context, Result, Store};
use base64::Engine;

pub fn save_skin(store: &Store, data: &str) -> Result<String> {
    let bytes = storage::png_data(data, false)?;
    let name = format!("skin-exports/Skin-{}.png", uuid::Uuid::new_v4());
    let path = storage::relative(&store.root, &name)?;
    storage::atomic(&path, &bytes)?;
    Ok(path.to_string_lossy().into_owned())
}

pub fn save(store: &Store, data: &str) -> Result<String> {
    if data.len() > 16_000_000 {
        return Err("Studio image exceeds 12 MB".into());
    }
    let encoded = data
        .strip_prefix("data:image/png;base64,")
        .ok_or("Expected PNG image")?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .context("Decode studio image")?;
    if bytes.len() < 33 || !bytes.starts_with(b"\x89PNG\r\n\x1a\n") || &bytes[12..16] != b"IHDR" {
        return Err("Invalid PNG header".into());
    }
    let w = u32::from_be_bytes(bytes[16..20].try_into().unwrap());
    let h = u32::from_be_bytes(bytes[20..24].try_into().unwrap());
    if !(1..=4096).contains(&w) || !(1..=4096).contains(&h) {
        return Err("Invalid studio image dimensions".into());
    }
    let name = format!(
        "studio-shots/Votex-{}-{}.png",
        chrono::Utc::now().format("%Y%m%d-%H%M%S"),
        uuid::Uuid::new_v4()
    );
    let path = storage::relative(&store.root, &name)?;
    storage::atomic(&path, &bytes)?;
    Ok(path.to_string_lossy().into_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn skin_export_preserves_png_bytes_and_rejects_non_skins() {
        let dir=tempfile::tempdir().unwrap();
        let store=Store::new(dir.path().join("votex")).unwrap();
        let png=include_bytes!("../../public/assets/votex-default-skin.png");
        let data=format!("data:image/png;base64,{}",base64::engine::general_purpose::STANDARD.encode(png));
        let path=save_skin(&store,&data).unwrap();
        assert!(std::path::Path::new(&path).starts_with(store.root.join("skin-exports")));
        assert_eq!(std::fs::read(path).unwrap(),png);
        let mut bad=png.to_vec();bad[16..20].copy_from_slice(&1024u32.to_be_bytes());
        assert!(save_skin(&store,&format!("data:image/png;base64,{}",base64::engine::general_purpose::STANDARD.encode(bad))).is_err());
        assert!(save_skin(&store,"data:text/html;base64,AAAA").is_err());
    }
    #[test]
    fn image_export_is_bounded_and_preserves_real_png() {
        let dir = tempfile::tempdir().unwrap();
        let store = Store::new(dir.path().join("votex")).unwrap();
        let png = include_bytes!("../../public/assets/votex-studio-skin-v3.png");
        let data = format!(
            "data:image/png;base64,{}",
            base64::engine::general_purpose::STANDARD.encode(png)
        );
        let path = save(&store, &data).unwrap();
        assert!(std::path::Path::new(&path).starts_with(store.root.join("studio-shots")));
        assert_eq!(std::fs::read(path).unwrap(), png);
        assert!(save(&store, "data:image/png;base64,AAAA").is_err());
        assert!(save(&store, "data:text/html;base64,AAAA").is_err());
        let mut oversized = png.to_vec();
        oversized[16..20].copy_from_slice(&5000u32.to_be_bytes());
        assert!(save(
            &store,
            &format!(
                "data:image/png;base64,{}",
                base64::engine::general_purpose::STANDARD.encode(oversized)
            )
        )
        .is_err());
    }
}
