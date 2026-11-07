fn votex_banner(pixels: &[u8]) -> Vec<u8> {
    // Reuse the upstream extrusion, outline, rim and sampled palette.
    const MASK: [&[u8]; 7] = [
        b"100010011100111110111110100010000001111010000011111011111010001011111",
        b"100010110110001000100000100010000011000010000000100010000011001000100",
        b"100010100010001000100000010100000010000010000000100010000011001000100",
        b"100010100010001000111100001000000010000010000000100011110010101000100",
        b"100010100010001000100000010100000010000010000000100010000010011000100",
        b"010100110110001000100000100010000011000010000000100010000010011000100",
        b"001000011100001000111110100010000001111011111011111011111010001000100",
    ];
    const SCALE_X: usize = 7;
    const SCALE_Y: usize = 5;
    let left = (WIDTH - MASK[0].len() * SCALE_X) / 2;
    let mut faces = vec![false; WIDTH * HEIGHT];
    for (row, glyphs) in MASK.iter().enumerate() {
        for (column, &bit) in glyphs.iter().enumerate() {
            if bit == b'1' {
                let start = left + column * SCALE_X;
                for y in FACE_TOP + row * SCALE_Y..FACE_TOP + (row + 1) * SCALE_Y {
                    faces[y * WIDTH + start..y * WIDTH + start + SCALE_X].fill(true);
                }
            }
        }
    }
    let palette = Palette::sample(pixels);
    let mut out = vec![0; WIDTH * HEIGHT * 4];
    draw_glyphs(&mut out, &faces, &palette, 0..WIDTH);
    fill_counters(&mut out, palette.outline);
    soften_edges(&mut out, palette.soft);
    out
}
