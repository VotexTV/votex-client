package dev.votex.appearance;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapeAtlasTest {
 @Test void portraitFillsBothCapeFacesAndEdgesWithoutChangingSource(){
  var source=new BufferedImage(10,16,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<16;y++)for(int x=0;x<10;x++)source.setRGB(x,y,0xff000000|(x*23)<<16|(y*15)<<8);
  int original=source.getRGB(9,15);var atlas=CapeAtlas.create(source,CapeAtlas.crop(10,16,10d/16,1,.5,.5),false);
  assertEquals(64,atlas.getWidth());assertEquals(32,atlas.getHeight());for(int y=0;y<16;y++)for(int x=0;x<10;x++){assertEquals(source.getRGB(x,y),atlas.getRGB(1+x,1+y));assertEquals(source.getRGB(x,y),atlas.getRGB(12+x,1+y));}
  assertEquals(source.getRGB(0,8),atlas.getRGB(0,9));assertEquals(source.getRGB(9,8),atlas.getRGB(11,9));assertEquals(source.getRGB(5,15),atlas.getRGB(16,0));assertEquals(0,atlas.getRGB(50,25));assertEquals(original,source.getRGB(9,15));
 }
 @Test void readyMadeAtlasRemainsPixelExact(){var source=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);source.setRGB(24,18,0x88665544);var atlas=CapeAtlas.create(source,CapeAtlas.crop(64,32,2,1,.5,.5),true);assertEquals(0x88665544,atlas.getRGB(24,18));assertEquals(0,atlas.getRGB(23,18));}
 @Test void zoomAndOffscreenPanStayInsideSource(){for(double ratio:new double[]{.625,2})for(double cx:new double[]{-1,.5,2})for(double cy:new double[]{-1,.5,2}){var c=CapeAtlas.crop(100,80,ratio,4,cx,cy);assertTrue(c.x()>=0&&c.y()>=0&&c.x()+c.width()<=100.000001&&c.y()+c.height()<=80.000001);assertEquals(ratio,c.width()/c.height(),.000001);}assertThrows(IllegalArgumentException.class,()->CapeAtlas.crop(10,10,2,Double.NaN,.5,.5));}
}
