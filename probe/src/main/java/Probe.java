import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.localization.*;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.channel.*;
import org.schabi.newpipe.extractor.channel.tabs.*;
import org.schabi.newpipe.extractor.linkhandler.*;
import java.util.*;

public class Probe {
  static okhttp3.OkHttpClient c = new okhttp3.OkHttpClient();
  public static void main(String[] a) throws Exception {
    NewPipe.init(new Downloader() {
      public Response execute(Request r) throws java.io.IOException {
        okhttp3.RequestBody b = r.dataToSend() == null ? null : okhttp3.RequestBody.create(r.dataToSend());
        okhttp3.Request.Builder rb = new okhttp3.Request.Builder().method(r.httpMethod(), b).url(r.url())
          .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0");
        for (Map.Entry<String, List<String>> e : r.headers().entrySet()) { rb.removeHeader(e.getKey()); for (String v : e.getValue()) rb.addHeader(e.getKey(), v); }
        try (okhttp3.Response rs = c.newCall(rb.build()).execute()) {
          String body = rs.body() == null ? null : rs.body().string();
          return new Response(rs.code(), rs.message(), rs.headers().toMultimap(), body, rs.request().url().toString());
        }
      }
    }, new Localization("es", "PE"), new ContentCountry("PE"));
    StreamingService yt = ServiceList.YouTube;
    List<StreamInfoItem> shorts = new ArrayList<>();
    for (String q : new String[]{"cumbia", "música #shorts"}) {
      try {
        SearchInfo info = SearchInfo.getInfo(yt, yt.getSearchQHFactory().fromQuery(q));
        int n = 0, flagged = 0, url = 0, d60 = 0, d180 = 0;
        for (InfoItem it : info.getRelatedItems()) {
          if (!(it instanceof StreamInfoItem s)) continue;
          n++;
          if (s.isShortFormContent()) flagged++;
          if (s.getUrl().contains("/shorts/")) url++;
          if (s.getDuration() > 0 && s.getDuration() <= 60) d60++;
          if (s.getDuration() > 0 && s.getDuration() <= 180) d180++;
          System.out.println("   " + s.getDuration() + "s short=" + s.isShortFormContent() + " " + s.getUrl() + " | " + s.getName());
          if (s.isShortFormContent() || s.getUrl().contains("/shorts/") || (s.getDuration() > 0 && s.getDuration() <= 60)) shorts.add(s);
        }
        System.out.println("Q[" + q + "] total=" + info.getRelatedItems().size() + " streams=" + n + " flagged=" + flagged + " urlShorts=" + url + " <=60=" + d60 + " <=180=" + d180 + " errors=" + info.getErrors());
      } catch (Exception e) { System.out.println("Q[" + q + "] ERROR " + e); }
    }
    try {
      ChannelInfo ch = ChannelInfo.getInfo(yt, "https://www.youtube.com/@LasEstrellasDeLaCumbia");
      System.out.println("CHANNEL " + ch.getName() + " tabs=" + ch.getTabs().stream().map(t -> t.getContentFilters().toString()).toList());
      for (ListLinkHandler t : ch.getTabs()) {
        if (t.getContentFilters().contains(ChannelTabs.SHORTS)) {
          ChannelTabInfo ti = ChannelTabInfo.getInfo(yt, t);
          System.out.println("CHANNEL shorts tab items=" + ti.getRelatedItems().size() + " errors=" + ti.getErrors());
          for (InfoItem it : ti.getRelatedItems().subList(0, Math.min(3, ti.getRelatedItems().size()))) System.out.println("   " + it.getUrl());
        }
      }
    } catch (Exception e) { System.out.println("CHANNEL ERROR " + e); }
    for (String params : new String[]{"EgIQCQ%3D%3D", "EgIQCQ=="}) {
      for (String q : new String[]{"cumbia", "humor"}) {
        String body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20251105.01.00\",\"hl\":\"es\",\"gl\":\"PE\"}},\"query\":\"" + q + "\",\"params\":\"" + java.net.URLDecoder.decode(params, "UTF-8") + "\"}";
        okhttp3.Request rq = new okhttp3.Request.Builder().url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
          .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.get("application/json")))
          .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0").build();
        try (okhttp3.Response rs = c.newCall(rq).execute()) {
          String js = rs.body().string();
          java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"reelWatchEndpoint\":\\{\"videoId\":\"([\\w-]{11})").matcher(js);
          Set<String> ids = new LinkedHashSet<>(); while (m.find()) ids.add(m.group(1));
          Map<String,Integer> keys = new TreeMap<>();
          java.util.regex.Matcher k = java.util.regex.Pattern.compile("\"(\\w+(Renderer|ViewModel))\"").matcher(js);
          while (k.find()) keys.merge(k.group(1), 1, Integer::sum);
          System.out.println("RAW[" + q + "] http=" + rs.code() + " len=" + js.length() + " reelIds=" + ids.size() + " " + ids);
          System.out.println("   keys=" + keys);
          if (!ids.isEmpty()) {
            String id = ids.iterator().next();
            int at = js.indexOf(id);
            System.out.println("   ctx=" + js.substring(Math.max(0, at - 1500), Math.min(js.length(), at + 300)).replaceAll("\\s+"," "));
            shorts.add(null);
            try {
              StreamInfo si = StreamInfo.getInfo(yt, "https://www.youtube.com/shorts/" + id);
              VideoStream best = null;
              for (VideoStream v : si.getVideoOnlyStreams()) if (best == null || v.getHeight() > best.getHeight()) best = v;
              System.out.println("STREAM ok " + id + " " + si.getName() + " videoOnly=" + si.getVideoOnlyStreams().size() + " best=" + (best == null ? "-" : best.getWidth() + "x" + best.getHeight()));
            } catch (Exception e) { System.out.println("STREAM ERROR " + id + " " + e); }
          }
        }
      }
      break;
    }
    shorts.removeIf(Objects::isNull);
    for (StreamInfoItem s : shorts.subList(0, Math.min(3, shorts.size()))) {
      try {
        StreamInfo si = StreamInfo.getInfo(yt, s.getUrl());
        VideoStream best = null;
        for (VideoStream v : si.getVideoOnlyStreams()) if (best == null || v.getHeight() > best.getHeight()) best = v;
        System.out.println("STREAM ok " + s.getUrl() + " videoOnly=" + si.getVideoOnlyStreams().size() + " best=" + (best == null ? "-" : best.getWidth() + "x" + best.getHeight()));
      } catch (Exception e) { System.out.println("STREAM ERROR " + s.getUrl() + " " + e); }
    }
  }
}
