package dev.votex.addons;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded static catalogue images become small, metadata-free RGBA PNGs on IO workers.
 * This does not widen PNG-only skin/cape/network validators or change global ImageIO plugins.
 */
public final class IconImage {
 public static final int MAX_BYTES=512*1024,MAX_DIMENSION=1024,THUMBNAIL=128;
 private IconImage(){}
 public static byte[] thumbnail(byte[] encoded)throws IOException {
  if(encoded==null||encoded.length<12||encoded.length>MAX_BYTES)return null;
  String format=format(encoded);if(format==null||animated(encoded,format))return null;
  ImageReader reader;
  if(format.equals("webp"))reader=new WebPImageReaderSpi().createReaderInstance();
  else {var readers=ImageIO.getImageReadersByFormatName(format);if(!readers.hasNext())return null;reader=readers.next();}
  try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))){
   reader.setInput(input,false,true);
   int width=reader.getWidth(0),height=reader.getHeight(0);
   if(width<1||height<1||width>MAX_DIMENSION||height>MAX_DIMENSION)return null;
   BufferedImage decoded=reader.read(0);
   if(decoded==null||decoded.getWidth()!=width||decoded.getHeight()!=height)return null;
   double ratio=Math.min(1,THUMBNAIL/(double)Math.max(width,height));
   BufferedImage clean=new BufferedImage(Math.max(1,(int)Math.round(width*ratio)),Math.max(1,(int)Math.round(height*ratio)),BufferedImage.TYPE_INT_ARGB);
   var graphics=clean.createGraphics();
   try{graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);graphics.drawImage(decoded,0,0,clean.getWidth(),clean.getHeight(),null);}
   finally{graphics.dispose();decoded.flush();}
   try(var output=new ByteArrayOutputStream()){
    if(!ImageIO.write(clean,"PNG",output))throw new IOException("PNG icon writer unavailable");
    byte[] png=output.toByteArray();return png.length<=MAX_BYTES&&AddonRules.png(png)?png:null;
   }finally{clean.flush();}
  }catch(RuntimeException invalid){throw new IOException("Invalid catalogue image",invalid);}
  finally{reader.dispose();}
 }
 private static String format(byte[] b){
  if(b.length>=24&&Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "PNG";
  if((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "JPEG";
  if(tag(b,0,"RIFF")&&tag(b,8,"WEBP"))return "webp";
  return null;
 }
 private static boolean tag(byte[] b,int offset,String value){if(offset<0||offset+4>b.length)return false;for(int i=0;i<4;i++)if(b[offset+i]!=(byte)value.charAt(i))return false;return true;}
 private static boolean animated(byte[] b,String format){
  if(format.equals("webp")){
   for(long pos=12;pos+8<=b.length;){int p=(int)pos;long length=(b[p+4]&255L)|((b[p+5]&255L)<<8)|((b[p+6]&255L)<<16)|((b[p+7]&255L)<<24);
    if(tag(b,p,"ANIM")||tag(b,p,"ANMF")||tag(b,p,"VP8X")&&length>=1&&p+8<b.length&&(b[p+8]&2)!=0)return true;
    pos+=8+length+(length&1);if(pos>b.length)return true;
   }
  }else if(format.equals("PNG")){
   for(long pos=8;pos+12<=b.length;){int p=(int)pos;long length=((b[p]&255L)<<24)|((b[p+1]&255L)<<16)|((b[p+2]&255L)<<8)|(b[p+3]&255L);
    if(tag(b,p+4,"acTL"))return true;pos+=12+length;if(pos>b.length)return true;
   }
  }
  return false;
 }
}
