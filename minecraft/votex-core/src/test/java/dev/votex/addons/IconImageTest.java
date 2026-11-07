package dev.votex.addons;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IconImageTest {
 private byte[] fixture(String name)throws Exception{try(var stream=getClass().getResourceAsStream("/dev/votex/addons/icons/"+name)){assertNotNull(stream);return stream.readAllBytes();}}
 @Test void real_lossless_and_lossy_webp_become_png_with_correct_pixels_and_alpha()throws Exception{
  var lossless=ImageIO.read(new ByteArrayInputStream(IconImage.thumbnail(fixture("lossless.webp"))));
  assertEquals(8,lossless.getWidth());assertEquals(4,lossless.getHeight());assertEquals(0xffff0000,lossless.getRGB(0,0));assertEquals(128,(lossless.getRGB(7,0)>>>24));assertEquals(0,lossless.getRGB(7,3)>>>24);
  var lossy=ImageIO.read(new ByteArrayInputStream(IconImage.thumbnail(fixture("lossy.webp"))));
  assertEquals(64,lossy.getWidth());int rgb=lossy.getRGB(20,10);assertEquals(180,(rgb>>>16)&255,5);assertEquals(100,(rgb>>>8)&255,5);assertEquals(60,rgb&255,5);
  var alpha=ImageIO.read(new ByteArrayInputStream(IconImage.thumbnail(fixture("alpha-lossy.webp"))));assertEquals(96,alpha.getRGB(10,10)>>>24);
 }
 @Test void png_and_jpeg_scale_without_stretching_and_strip_source_metadata()throws Exception{
  byte[] png=IconImage.thumbnail(fixture("wide.png"));assertTrue(AddonRules.png(png));var wide=ImageIO.read(new ByteArrayInputStream(png));assertEquals(128,wide.getWidth());assertEquals(64,wide.getHeight());assertFalse(new String(png,StandardCharsets.ISO_8859_1).contains("private-comment"));
  byte[] jpeg=IconImage.thumbnail(fixture("sample.jpg"));assertTrue(AddonRules.png(jpeg));var converted=ImageIO.read(new ByteArrayInputStream(jpeg));assertEquals(64,converted.getWidth());assertEquals(32,converted.getHeight());
 }
 @Test void animations_oversized_sources_and_unknown_formats_are_rejected_before_texture_creation()throws Exception{
  for(String name:new String[]{"animated.webp","animated.png","oversize.png","oversize.webp"})assertNull(IconImage.thumbnail(fixture(name)),name);
  assertNull(IconImage.thumbnail(new byte[IconImage.MAX_BYTES+1]));assertNull(IconImage.thumbnail("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)));
  assertNull(IconImage.thumbnail(null));assertNull(IconImage.thumbnail(new byte[0]));
 }
}
