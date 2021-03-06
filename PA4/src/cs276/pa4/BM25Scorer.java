package cs276.pa4;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Skeleton code for the implementation of a BM25 Scorer in Task 2.
 */
public class BM25Scorer extends AScorer {

  double urlweight = 0.2;
  double titleweight = 1.0;
  double bodyweight = 0.2;
  double headerweight = 0.5;
  double anchorweight = 0.5;

  // BM25-specific weights
  double burl = 0.5;
  double btitle = 0.7;
  double bheader = 0.8;
  double bbody = 0.8;
  double banchor = 0.4;

  double k1 = 5.0;
  double pageRankLambda = 1.0;
  double pageRankLambdaPrime = 0.7;

  // query -> url -> document
  Map<Query, Map<String, Document>> queryDict;

  // BM25 data structures--feel free to modify these
  // Document -> field -> length
  Map<Document, Map<String, Double>> lengths;

  // field name -> average length
  Map<String, Double> avgLengths;

  // Document -> pagerank score
  Map<Document, Double> pagerankScores;

  /**
   * Construct a BM25Scorer.
   * 
   * @param idfs
   *          the map of idf scores
   * @param queryDict
   *          a map of query to url to document
   */
  public BM25Scorer(Map<String, Double> idfs,
      Map<Query, Map<String, Document>> queryDict) {
    super(idfs);
    this.sublinearTF = false;
    this.queryDict = queryDict;
    this.calcAverageLengths();
  }

  private static double countNonEmpty(String[] tokens) {
    double count = 0;
    for (String token : tokens) {
      if (!token.isEmpty()) {
        count += 1;
      }
    }
    return count;
  }

  private static Map<String, Double> getDocFieldLengths(Document doc) {
    Map<String, Double> fieldLengths = new HashMap<>();
    if (doc.url == null) {
      fieldLengths.put("url", 0.0);
    } else {
      String[] tokens = doc.url.split("[^0-9a-zA-Z]+");
      double count = countNonEmpty(tokens);
      fieldLengths.put("url", count);
    }

    if (doc.title == null) {
      fieldLengths.put("title", 0.0);
    } else {
      String[] tokens = doc.title.split("\\s+");
      double count = countNonEmpty(tokens);
      fieldLengths.put("title", count);
    }

    fieldLengths.put("body", (double) doc.body_length);

    if (doc.headers == null) {
      fieldLengths.put("header", 0.0);
    } else {
      double count = 0;
      for (String header : doc.headers) {
        String[] tokens = header.split("\\s+");
        count += countNonEmpty(tokens);
      }
      fieldLengths.put("header", count);
    }

    if (doc.anchors == null) {
      fieldLengths.put("anchor", 0.0);
    } else {
      double count = 0;
      for (Map.Entry<String, Integer> e : doc.anchors.entrySet()) {
        String anchorText = e.getKey();
        double anchorCount = e.getValue();
        String[] tokens = anchorText.split("\\s+");
        count += countNonEmpty(tokens) * anchorCount;
      }
      fieldLengths.put("anchor", count);
    }
    return fieldLengths;
  }

  /**
   * Set up average lengths for BM25, also handling PageRank.
   */
  public void calcAverageLengths() {
    lengths = new HashMap<>();
    avgLengths = new HashMap<>();
    pagerankScores = new HashMap<>();

    for (String tfType : this.TFTYPES) {
      avgLengths.put(tfType, 0.0);
    }

    for (Map<String, Document> docs : queryDict.values()) {
      for (Document doc : docs.values()) {
        // handle pagerank
        double vPageRank = Math.log(pageRankLambdaPrime + doc.page_rank);
        pagerankScores.put(doc, vPageRank);

        // calculate lengths of fields
        Map<String, Double> fieldLengths = getDocFieldLengths(doc);
        lengths.put(doc, fieldLengths);

        // accumulate lengths of fields
        for (String tfType : this.TFTYPES) {
          double newCount = avgLengths.get(tfType) + fieldLengths.get(tfType);
          avgLengths.put(tfType, newCount);
        }
      }
    }

    for (String tfType : this.TFTYPES) {
      double avgLength = avgLengths.get(tfType) / lengths.size();
      avgLengths.put(tfType, avgLength);
    }
  }

  /**
   * Get the net score.
   * 
   * @param tfs
   *          the term frequencies
   * @param q
   *          the Query
   * @param tfQuery
   * @param d
   *          the Document
   * @return the net score
   */
  public double getNetScore(Map<String, Map<String, Double>> tfs, Query q,
      Map<String, Double> tfQuery, Document d) {
    double score = 0.0;

    for (String term : tfQuery.keySet()) {
      double wdt = urlweight * tfs.get("url").get(term)
          + titleweight * tfs.get("title").get(term)
          + bodyweight * tfs.get("body").get(term)
          + headerweight * tfs.get("header").get(term)
          + anchorweight * tfs.get("anchor").get(term);

      score += wdt / (wdt + k1) * getIdf(term);
    }

    score += pageRankLambda * pagerankScores.get(d);

    return score;
  }

  /**
   * Do BM25 Normalization.
   * 
   * @param tfs
   *          the term frequencies
   * @param d
   *          the Document
   * @param q
   *          the Query
   */
  public void normalizeTFs(Map<String, Map<String, Double>> tfs, Document d,
      Query q) {
    for (Map.Entry<String, Map<String, Double>> e1 : tfs.entrySet()) {
      String field = e1.getKey();

      double bf;
      switch (field) {
      case "url":
        bf = burl;
        break;
      case "title":
        bf = btitle;
        break;
      case "body":
        bf = bbody;
        break;
      case "header":
        bf = bheader;
        break;
      case "anchor":
        bf = banchor;
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unexpected field '%s'", field));
      }

      double invLengthNormComp;
      if (avgLengths.get(field) == 0) {
        invLengthNormComp = 0;
      } else {
        invLengthNormComp = 1.0 / (1
            + bf * ((lengths.get(d).get(field) / avgLengths.get(field)) - 1));
      }

      for (Map.Entry<String, Double> e2 : e1.getValue().entrySet()) {
        e2.setValue(invLengthNormComp * e2.getValue());
      }
    }
  }

  /**
   * Write the tuned parameters of BM25 to file. Only used for grading purpose,
   * you should NOT modify this method.
   * 
   * @param filePath
   *          the output file path.
   */
  private void writeParaValues(String filePath) {
    try {
      File file = new File(filePath);
      if (!file.exists()) {
        file.createNewFile();
      }
      FileWriter fw = new FileWriter(file.getAbsoluteFile());
      String[] names = { "urlweight", "titleweight", "bodyweight",
          "headerweight", "anchorweight", "burl", "btitle", "bheader", "bbody",
          "banchor", "k1", "pageRankLambda", "pageRankLambdaPrime" };
      double[] values = { this.urlweight, this.titleweight, this.bodyweight,
          this.headerweight, this.anchorweight, this.burl, this.btitle,
          this.bheader, this.bbody, this.banchor, this.k1, this.pageRankLambda,
          this.pageRankLambdaPrime };
      BufferedWriter bw = new BufferedWriter(fw);
      for (int idx = 0; idx < names.length; ++idx) {
        bw.write(names[idx] + " " + values[idx]);
        bw.newLine();
      }
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  /**
   * Get the similarity score.
   * 
   * @param d
   *          the Document
   * @param q
   *          the Query
   * @return the similarity score
   */
  public double getSimScore(Document d, Query q) {
    Map<String, Map<String, Double>> tfs = this.getDocTermFreqs(d, q);
    this.normalizeTFs(tfs, d, q);
    Map<String, Double> tfQuery = getQueryFreqs(q);

    // Write out the tuned BM25 parameters
    // This is only used for grading purposes.
    // You should NOT modify the writeParaValues method.
    writeParaValues("bm25Para.txt");
    return getNetScore(tfs, q, tfQuery, d);
  }

}
