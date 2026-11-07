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
    for (String q : new String[]{"música #shorts", "humor #shorts", "shorts cumbia"}) {
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
      for (ListLinkHandler t : ch.getTabs()) {
        if (t.getContentFilters().contains(ChannelTabs.SHORTS)) {
          ChannelTabInfo ti = ChannelTabInfo.getInfo(yt, t);
          System.out.println("CHANNEL shorts tab items=" + ti.getRelatedItems().size() + " errors=" + ti.getErrors());
          for (InfoItem it : ti.getRelatedItems().subList(0, Math.min(3, ti.getRelatedItems().size()))) System.out.println("   " + it.getUrl());
        }
      }
    } catch (Exception e) { System.out.println("CHANNEL ERROR " + e); }
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
