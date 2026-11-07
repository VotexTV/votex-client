package dev.votex.client;

import com.google.gson.*;
import dev.votex.addons.AddonRules;
import dev.votex.addons.IconImage;
import dev.votex.addons.CurseForgeRules;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Optional official CurseForge adapter. The launcher supplies its saved vault key in memory only. */
final class CurseForgeClient {
 private static final String KEY_ENV="VOTEX_CURSEFORGE_API_KEY";
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
 record Resolved(CurseForgeRules.Project project,CurseForgeRules.File file){}
 private CurseForgeClient(){}
 static boolean configured(){return CurseForgeRules.validKey(System.getenv(KEY_ENV));}
 private static String key()throws IOException{String key=System.getenv(KEY_ENV);if(!CurseForgeRules.validKey(key))throw new IOException("CURSEFORGE_KEY_REQUIRED: Hinterlege deinen eigenen Schlüssel im Launcher unter Mods → Quellen und starte Minecraft anschließend neu.");return key;}
 static ModLibraryService.Search search(String query,String kind,int offset,int limit)throws Exception{if(offset>=10000)return new ModLibraryService.Search(List.of(),10000,offset);var result=CurseForgeRules.search(get(CurseForgeRules.searchRoute(query,kind,offset,limit)),kind,offset);return new ModLibraryService.Search(result.projects().stream().map(project->new ModLibraryService.Project(Long.toString(project.id()),project.name(),project.summary()+(Boolean.FALSE.equals(project.distributionAllowed())?" · Automatischer Download vom Autor gesperrt":""),project.kind(),project.icon(),project.downloads(),"curseforge")).toList(),result.total(),result.offset());}
 static List<Resolved> resolve(ModLibraryService.Project requested)throws Exception{
  var queue=new ArrayDeque<Long>();queue.add(CurseForgeRules.id(requested.id()));var result=new LinkedHashMap<Long,Resolved>();long total=0;
  while(!queue.isEmpty()){long id=queue.removeFirst();if(result.containsKey(id))continue;if(result.size()>=40)throw new IOException("Zu viele erforderliche CurseForge-Projekte (maximal 40)");var project=CurseForgeRules.project(get("mods/"+id).getAsJsonObject("data"));if(project.id()!=id)throw new IOException("CurseForge-Projektkennung stimmt nicht überein");if(id==CurseForgeRules.id(requested.id())&&!project.kind().equals(requested.kind()))throw new IOException("CurseForge-Projektklasse wurde geändert");CurseForgeRules.distribution(project);var file=CurseForgeRules.select(get(CurseForgeRules.filesRoute(id,project.kind())),project);if((total+=file.size())>AddonRules.MAX_TOTAL)throw new IOException("CurseForge-Installationsplan überschreitet 512 MiB");if(file.url().isEmpty()){String route="mods/"+id+"/files/"+file.id()+"/download-url";var value=get(route).get("data");if(value==null||value.isJsonNull()||value.getAsString().isBlank())throw new IOException("DISTRIBUTION_RESTRICTED: CurseForge stellt keinen autorisierten Download bereit. Bitte verwende die Projektseite und den lokalen Import.");String url=value.getAsString();CurseForgeRules.download(url);file=new CurseForgeRules.File(file.id(),file.project(),file.name(),file.display(),file.size(),file.sha1(),url,file.required(),file.incompatible());}result.put(id,new Resolved(project,file));for(long dependency:file.required())queue.addLast(dependency);
  }
  for(var resolved:result.values())for(long incompatible:resolved.file().incompatible())if(result.containsKey(incompatible))throw new IOException("Der CurseForge-Installationsplan enthält inkompatible Projekte");return List.copyOf(result.values());
 }
 static byte[] download(CurseForgeRules.File file)throws Exception{URI current=CurseForgeRules.download(file.url());String key=key();for(int redirect=0;redirect<=3;redirect++){var response=send(current,key,AddonRules.MAX_FILE);int status=response.statusCode();if(status>=300&&status<400){response.body().close();if(redirect==3)throw new IOException("Zu viele CurseForge-Download-Weiterleitungen");String location=response.headers().firstValue("Location").orElseThrow(()->new IOException("CurseForge-Weiterleitung enthält kein Ziel"));current=CurseForgeRules.download(current.resolve(location).toString());continue;}byte[] bytes=read(response,AddonRules.MAX_FILE);CurseForgeRules.verify(file,bytes);return bytes;}throw new IOException("CurseForge-Download fehlgeschlagen");}
 static byte[] icon(String url)throws Exception{var response=send(CurseForgeRules.icon(url),null,512*1024);byte[] bytes=read(response,512*1024);return IconImage.thumbnail(bytes);}
 private static JsonObject get(String route)throws Exception{var response=send(CurseForgeRules.api(route),key(),4*1024*1024);byte[] bytes=read(response,4*1024*1024);return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
 private static HttpResponse<InputStream> send(URI uri,String key,long max)throws Exception{var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(35)).header("User-Agent","VotexClient/0.4.1 (https://votex-client-project.adoredpeach257.chatgpt.site)").header("Accept",uri.getHost().equals("api.curseforge.com")?"application/json":"*/*");if(key!=null)request.header("x-api-key",key);var response=HTTP.send(request.GET().build(),HttpResponse.BodyHandlers.ofInputStream());if(response.headers().firstValueAsLong("Content-Length").orElse(0)>max){response.body().close();throw new IOException("CurseForge-Antwort überschreitet das Größenlimit");}return response;}
 private static byte[] read(HttpResponse<InputStream> response,long max)throws IOException{try(var input=response.body();var bytes=new ByteArrayOutputStream()){if(response.statusCode()!=200)throw new IOException("CurseForge HTTP "+response.statusCode()+". Prüfe den eingerichteten Zugang und die Freigabe des Autors.");byte[] buffer=new byte[16384];long total=0;int count;while((count=input.read(buffer))!=-1){if((total+=count)>max)throw new IOException("CurseForge-Datei überschreitet das Größenlimit");bytes.write(buffer,0,count);}return bytes.toByteArray();}}
}
