package de.lmu.ifi.dbs.elki.algorithm.outlier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.DistanceResultPair;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.PrimitiveSimilarityFunction;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel.KernelMatrix;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel.PolynomialKernelFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.outlier.InvertedOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import de.lmu.ifi.dbs.elki.utilities.pairs.FCPair;

/**
 * Angle-Based Outlier Detection
 * 
 * Outlier detection using variance analysis on angles, especially for high
 * dimensional data sets.
 * 
 * H.-P. Kriegel, M. Schubert, and A. Zimek: Angle-Based Outlier Detection in
 * High-dimensional Data. In: Proc. 14th ACM SIGKDD Int. Conf. on Knowledge
 * Discovery and Data Mining (KDD '08), Las Vegas, NV, 2008.
 * 
 * @author Matthias Schubert (Original Code)
 * @author Erich Schubert (ELKIfication)
 * 
 * @apiviz.has KNNQuery
 * 
 * @param <V> Vector type
 */
@Title("ABOD: Angle-Based Outlier Detection")
@Description("Outlier detection using variance analysis on angles, especially for high dimensional data sets.")
@Reference(authors = "H.-P. Kriegel, M. Schubert, and A. Zimek", title = "Angle-Based Outlier Detection in High-dimensional Data", booktitle = "Proc. 14th ACM SIGKDD Int. Conf. on Knowledge Discovery and Data Mining (KDD '08), Las Vegas, NV, 2008", url = "http://dx.doi.org/10.1145/1401890.1401946")
public class ABOD<V extends NumberVector<V, ?>> extends AbstractDistanceBasedAlgorithm<V, DoubleDistance, OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging logger = Logging.getLogger(ABOD.class);

  /**
   * Parameter for k, the number of neighbors used in kNN queries.
   */
  public static final OptionID K_ID = OptionID.getOrCreateOptionID("abod.k", "Parameter k for kNN queries.");

  /**
   * Parameter for sample size to be used in fast mode.
   */
  public static final OptionID FAST_SAMPLE_ID = OptionID.getOrCreateOptionID("abod.samplesize", "Sample size to enable fast mode.");

  /**
   * Parameter for the kernel function.
   */
  public static final OptionID KERNEL_FUNCTION_ID = OptionID.getOrCreateOptionID("abod.kernelfunction", "Kernel function to use.");

  /**
   * The preprocessor used to materialize the kNN neighborhoods.
   */
  public static final OptionID PREPROCESSOR_ID = OptionID.getOrCreateOptionID("abod.knnquery", "Processor to compute the kNN neighborhoods.");

  /**
   * Association ID for ABOD.
   */
  public static final AssociationID<Double> ABOD_SCORE = AssociationID.getOrCreateAssociationID("ABOD", Double.class);

  /**
   * use alternate code below
   */
  private static final boolean useRNDSample = false;

  /**
   * k parameter
   */
  private int k;

  /**
   * Variable to store fast mode sampling value.
   */
  int sampleSize = 0;

  /**
   * Store the configured Kernel version
   */
  private PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction;

  private ArrayModifiableDBIDs staticids = null;

  /**
   * Actual constructor, with parameters. Fast mode (sampling).
   * 
   * @param k k parameter
   * @param sampleSize sample size
   * @param primitiveKernelFunction Kernel function to use
   * @param distanceFunction Distance function
   */
  public ABOD(int k, int sampleSize, PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction, DistanceFunction<V, DoubleDistance> distanceFunction) {
    super(distanceFunction);
    this.k = k;
    this.sampleSize = sampleSize;
    this.primitiveKernelFunction = primitiveKernelFunction;
  }

  /**
   * Actual constructor, with parameters. Slow mode (exact).
   * 
   * @param k k parameter
   * @param primitiveKernelFunction kernel function to use
   * @param distanceFunction Distance function
   */
  public ABOD(int k, PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction, DistanceFunction<V, DoubleDistance> distanceFunction) {
    super(distanceFunction);
    this.k = k;
    this.sampleSize = 0;
    this.primitiveKernelFunction = primitiveKernelFunction;
  }

  /**
   * Main part of the algorithm. Exact version.
   * 
   * @param relation Relation to query
   * @param k k for kNN queries
   * @return result
   */
  public OutlierResult getRanking(Relation<V> relation, int k) {
    // Fix a static set of IDs
    staticids = DBIDUtil.newArray(relation.getDBIDs());
    Collections.sort(staticids);

    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, relation, staticids);
    PriorityQueue<FCPair<Double, DBID>> pq = new PriorityQueue<FCPair<Double, DBID>>(relation.size(), Collections.reverseOrder());

    // preprocess kNN neighborhoods
    assert (k == this.k);
    KNNQuery<V, DoubleDistance> knnQuery = QueryUtil.getKNNQuery(relation, getDistanceFunction(), k);

    for(DBID objKey : relation.iterDBIDs()) {
      MeanVariance s = new MeanVariance();

      // System.out.println("Processing: " +objKey);
      List<DistanceResultPair<DoubleDistance>> neighbors = knnQuery.getKNNForDBID(objKey, k);
      Iterator<DistanceResultPair<DoubleDistance>> iter = neighbors.iterator();
      while(iter.hasNext()) {
        DBID key1 = iter.next().getDBID();
        // Iterator iter2 = data.keyIterator();
        Iterator<DistanceResultPair<DoubleDistance>> iter2 = neighbors.iterator();
        // PriorityQueue best = new PriorityQueue(false, k);
        while(iter2.hasNext()) {
          DBID key2 = iter2.next().getDBID();
          if(key2.equals(key1) || key1.equals(objKey) || key2.equals(objKey)) {
            continue;
          }
          double nenner = calcDenominator(kernelMatrix, objKey, key1, key2);

          if(nenner != 0) {
            double sqrtnenner = Math.sqrt(nenner);
            double tmp = calcNumerator(kernelMatrix, objKey, key1, key2) / nenner;
            s.put(tmp, 1 / sqrtnenner);
          }

        }
      }
      // Sample variance probably would be correct, however the numerical
      // instabilities can actually break ABOD here.
      pq.add(new FCPair<Double, DBID>(s.getNaiveVariance(), objKey));
    }

    DoubleMinMax minmaxabod = new DoubleMinMax();
    WritableDataStore<Double> abodvalues = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC, Double.class);
    for(FCPair<Double, DBID> pair : pq) {
      abodvalues.put(pair.getSecond(), pair.getFirst());
      minmaxabod.put(pair.getFirst());
    }
    // Build result representation.
    AnnotationResult<Double> scoreResult = new AnnotationFromDataStore<Double>("Angle-based Outlier Degree", "abod-outlier", ABOD_SCORE, abodvalues);
    OutlierScoreMeta scoreMeta = new InvertedOutlierScoreMeta(minmaxabod.getMin(), minmaxabod.getMax(), 0.0, Double.POSITIVE_INFINITY);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  /**
   * Main part of the algorithm. Fast version.
   * 
   * @param relation Relation to use
   * @param k k for kNN queries
   * @param sampleSize Sample size
   * @return result
   */
  public OutlierResult getFastRanking(Relation<V> relation, int k, int sampleSize) {
    // Fix a static set of IDs
    staticids = DBIDUtil.newArray(relation.getDBIDs());
    Collections.sort(staticids);

    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, relation, staticids);

    PriorityQueue<FCPair<Double, DBID>> pq = new PriorityQueue<FCPair<Double, DBID>>(relation.size(), Collections.reverseOrder());
    // get Candidate Ranking
    for(DBID aKey : relation.iterDBIDs()) {
      HashMap<DBID, Double> dists = new HashMap<DBID, Double>(relation.size());
      // determine kNearestNeighbors and pairwise distances
      PriorityQueue<FCPair<Double, DBID>> nn;
      if(!useRNDSample) {
        nn = calcDistsandNN(relation, kernelMatrix, sampleSize, aKey, dists);
      }
      else {
        // alternative:
        nn = calcDistsandRNDSample(relation, kernelMatrix, sampleSize, aKey, dists);
      }

      // get normalization
      double[] counter = calcFastNormalization(aKey, dists);
      // System.out.println(counter[0] + " " + counter2[0] + " " + counter[1] +
      // " " + counter2[1]);
      // umsetzen von Pq zu list
      ModifiableDBIDs neighbors = DBIDUtil.newArray(nn.size());
      while(!nn.isEmpty()) {
        neighbors.add(nn.remove().getSecond());
      }
      // getFilter
      double var = getAbofFilter(kernelMatrix, aKey, dists, counter[1], counter[0], neighbors);
      pq.add(new FCPair<Double, DBID>(var, aKey));
      // System.out.println("prog "+(prog++));
    }
    // refine Candidates
    PriorityQueue<FCPair<Double, DBID>> resqueue = new PriorityQueue<FCPair<Double, DBID>>(k);
    // System.out.println(pq.size() + " objects ordered into candidate list.");
    int v = 0;
    while(!pq.isEmpty()) {
      if(resqueue.size() == k && pq.peek().getFirst() > resqueue.peek().getFirst()) {
        break;
      }
      // double approx = pq.peek().getFirst();
      DBID aKey = pq.remove().getSecond();
      // if(!result.isEmpty()) {
      // System.out.println("Best Candidate " + aKey+" : " + pq.firstPriority()
      // + " worst result: " + result.firstPriority());
      // } else {
      // System.out.println("Best Candidate " + aKey+" : " + pq.firstPriority()
      // + " worst result: " + Double.MAX_VALUE);
      // }
      v++;
      MeanVariance s = new MeanVariance();
      for(DBID bKey : relation.iterDBIDs()) {
        if(bKey.equals(aKey)) {
          continue;
        }
        for(DBID cKey : relation.iterDBIDs()) {
          if(cKey.equals(aKey)) {
            continue;
          }
          // double nenner = dists[y]*dists[z];
          double nenner = calcDenominator(kernelMatrix, aKey, bKey, cKey);
          if(nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, aKey, bKey, cKey) / nenner;
            double sqrtNenner = Math.sqrt(nenner);
            s.put(tmp, 1 / sqrtNenner);
          }
        }
      }
      // System.out.println( aKey + "Sum " + sum + " SQRSum " +sqrSum +
      // " Counter " + counter);
      double var = s.getSampleVariance();
      // System.out.println(aKey+ " : " + approx +" " + var);
      if(resqueue.size() < k) {
        resqueue.add(new FCPair<Double, DBID>(var, aKey));
      }
      else {
        if(resqueue.peek().getFirst() > var) {
          resqueue.remove();
          resqueue.add(new FCPair<Double, DBID>(var, aKey));
        }
      }

    }
    // System.out.println(v + " Punkte von " + data.size() + " verfeinert !!");
    DoubleMinMax minmaxabod = new DoubleMinMax();
    WritableDataStore<Double> abodvalues = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC, Double.class);
    for(FCPair<Double, DBID> pair : pq) {
      abodvalues.put(pair.getSecond(), pair.getFirst());
      minmaxabod.put(pair.getFirst());
    }
    // Build result representation.
    AnnotationResult<Double> scoreResult = new AnnotationFromDataStore<Double>("Angle-based Outlier Detection", "abod-outlier", ABOD_SCORE, abodvalues);
    OutlierScoreMeta scoreMeta = new InvertedOutlierScoreMeta(minmaxabod.getMin(), minmaxabod.getMax(), 0.0, Double.POSITIVE_INFINITY);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  // TODO: remove?
  @SuppressWarnings("unused")
  private double[] calcNormalization(Integer xKey, HashMap<Integer, Double> dists) {
    double[] result = new double[2];
    for(Integer yKey : dists.keySet()) {
      if(yKey.equals(xKey)) {
        continue;
      }
      for(Integer zKey : dists.keySet()) {
        if(zKey <= yKey) {
          continue;
        }
        if(zKey.equals(xKey)) {
          continue;
        }
        if(dists.get(yKey) != 0 && dists.get(zKey) != 0) {
          double sqr = Math.sqrt(dists.get(yKey) * dists.get(zKey));
          result[0] += 1 / sqr;
          result[1] += 1 / (dists.get(yKey) * dists.get(zKey) * sqr);
        }
      }
    }
    return result;
  }

  private double[] calcFastNormalization(@SuppressWarnings("unused") DBID x, HashMap<DBID, Double> dists) {
    double[] result = new double[2];

    double sum = 0;
    double sumF = 0;
    for(DBID yKey : dists.keySet()) {
      if(dists.get(yKey) != 0) {
        double tmp = 1 / Math.sqrt(dists.get(yKey));
        sum += tmp;
        sumF += (1 / dists.get(yKey)) * tmp;
      }
    }
    double sofar = 0;
    double sofarF = 0;
    for(DBID zKey : dists.keySet()) {
      if(dists.get(zKey) != 0) {
        double tmp = 1 / Math.sqrt(dists.get(zKey));
        sofar += tmp;
        double rest = sum - sofar;
        result[0] += tmp * rest;

        sofarF += (1 / dists.get(zKey)) * tmp;
        double restF = sumF - sofarF;
        result[1] += (1 / dists.get(zKey)) * tmp * restF;
      }
    }
    return result;
  }

  private double getAbofFilter(KernelMatrix kernelMatrix, DBID aKey, HashMap<DBID, Double> dists, double fulCounter, double counter, DBIDs neighbors) {
    double sum = 0.0;
    double sqrSum = 0.0;
    double partCounter = 0;
    Iterator<DBID> iter = neighbors.iterator();
    while(iter.hasNext()) {
      DBID bKey = iter.next();
      if(bKey.equals(aKey)) {
        continue;
      }
      Iterator<DBID> iter2 = neighbors.iterator();
      while(iter2.hasNext()) {
        DBID cKey = iter2.next();
        if(cKey.equals(aKey)) {
          continue;
        }
        if(bKey.compareTo(cKey) > 0) {
          double nenner = dists.get(bKey).doubleValue() * dists.get(cKey).doubleValue();
          if(nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, aKey, bKey, cKey) / nenner;
            double sqrtNenner = Math.sqrt(nenner);
            sum += tmp * (1 / sqrtNenner);
            sqrSum += tmp * tmp * (1 / sqrtNenner);
            partCounter += (1 / (sqrtNenner * nenner));
          }
        }
      }
    }
    // TODO: Document the meaning / use of fulCounter, partCounter.
    double mu = (sum + (fulCounter - partCounter)) / counter;
    return (sqrSum / counter) - (mu * mu);
  }

  /**
   * Compute the cosinus value between vectors aKey and bKey.
   * 
   * @param kernelMatrix
   * @param aKey
   * @param bKey
   * @return cosinus value
   */
  private double calcCos(KernelMatrix kernelMatrix, DBID aKey, DBID bKey) {
    final int ai = mapDBID(aKey);
    final int bi = mapDBID(bKey);
    return kernelMatrix.getDistance(ai, ai) + kernelMatrix.getDistance(bi, bi) - 2 * kernelMatrix.getDistance(ai, bi);
  }

  private int mapDBID(DBID aKey) {
    // TODO: this is not the most efficient...
    int off = Collections.binarySearch(staticids, aKey);
    if(off < 0) {
      throw new AbortException("Did not find id " + aKey.toString() + " in staticids. " + staticids.contains(aKey));
    }
    return off + 1;
  }

  private double calcDenominator(KernelMatrix kernelMatrix, DBID aKey, DBID bKey, DBID cKey) {
    return calcCos(kernelMatrix, aKey, bKey) * calcCos(kernelMatrix, aKey, cKey);
  }

  private double calcNumerator(KernelMatrix kernelMatrix, DBID aKey, DBID bKey, DBID cKey) {
    final int ai = mapDBID(aKey);
    final int bi = mapDBID(bKey);
    final int ci = mapDBID(cKey);
    return (kernelMatrix.getDistance(ai, ai) + kernelMatrix.getDistance(bi, ci) - kernelMatrix.getDistance(ai, ci) - kernelMatrix.getDistance(ai, bi));
  }

  private PriorityQueue<FCPair<Double, DBID>> calcDistsandNN(Relation<V> data, KernelMatrix kernelMatrix, int sampleSize, DBID aKey, HashMap<DBID, Double> dists) {
    PriorityQueue<FCPair<Double, DBID>> nn = new PriorityQueue<FCPair<Double, DBID>>(sampleSize);
    for(DBID bKey : data.iterDBIDs()) {
      double val = calcCos(kernelMatrix, aKey, bKey);
      dists.put(bKey, val);
      if(nn.size() < sampleSize) {
        nn.add(new FCPair<Double, DBID>(val, bKey));
      }
      else {
        if(val < nn.peek().getFirst()) {
          nn.remove();
          nn.add(new FCPair<Double, DBID>(val, bKey));
        }
      }
    }
    return nn;
  }

  private PriorityQueue<FCPair<Double, DBID>> calcDistsandRNDSample(Relation<V> data, KernelMatrix kernelMatrix, int sampleSize, DBID aKey, HashMap<DBID, Double> dists) {
    PriorityQueue<FCPair<Double, DBID>> nn = new PriorityQueue<FCPair<Double, DBID>>(sampleSize);
    int step = (int) ((double) data.size() / (double) sampleSize);
    int counter = 0;
    for(DBID bKey : data.iterDBIDs()) {
      double val = calcCos(kernelMatrix, aKey, bKey);
      dists.put(bKey, val);
      if(counter % step == 0) {
        nn.add(new FCPair<Double, DBID>(val, bKey));
      }
      counter++;
    }
    return nn;
  }

  /**
   * Get explanations for points in the database.
   * 
   * @param data to get explanations for
   */
  // TODO: this should be done by the result classes.
  public void getExplanations(Relation<V> data) {
    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, data, staticids);
    // PQ for Outlier Ranking
    PriorityQueue<FCPair<Double, DBID>> pq = new PriorityQueue<FCPair<Double, DBID>>(data.size(), Collections.reverseOrder());
    HashMap<DBID, LinkedList<DBID>> explaintab = new HashMap<DBID, LinkedList<DBID>>();
    // test all objects
    for(DBID objKey : data.iterDBIDs()) {
      MeanVariance s = new MeanVariance();
      // Queue for the best explanation
      PriorityQueue<FCPair<Double, DBID>> explain = new PriorityQueue<FCPair<Double, DBID>>();
      // determine Object
      // for each pair of other objects
      Iterator<DBID> iter = data.iterDBIDs();
      // Collect Explanation Vectors
      while(iter.hasNext()) {
        MeanVariance s2 = new MeanVariance();
        DBID key1 = iter.next();
        Iterator<DBID> iter2 = data.iterDBIDs();
        if(objKey.equals(key1)) {
          continue;
        }
        while(iter2.hasNext()) {
          DBID key2 = iter2.next();
          if(key2.equals(key1) || objKey.equals(key2)) {
            continue;
          }
          double nenner = calcDenominator(kernelMatrix, objKey, key1, key2);
          if(nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, objKey, key1, key2) / nenner;
            double sqr = Math.sqrt(nenner);
            s2.put(tmp, 1 / sqr);
          }
        }
        explain.add(new FCPair<Double, DBID>(s2.getSampleVariance(), key1));
        s.put(s2);
      }
      // build variance of the observed vectors
      pq.add(new FCPair<Double, DBID>(s.getSampleVariance(), objKey));
      //
      LinkedList<DBID> expList = new LinkedList<DBID>();
      expList.add(explain.remove().getSecond());
      while(!explain.isEmpty()) {
        DBID nextKey = explain.remove().getSecond();
        if(nextKey.equals(objKey)) {
          continue;
        }
        double max = Double.MIN_VALUE;
        for(DBID exp : expList) {
          if(exp.equals(objKey) || nextKey.equals(exp)) {
            continue;
          }
          double nenner = Math.sqrt(calcCos(kernelMatrix, objKey, nextKey)) * Math.sqrt(calcCos(kernelMatrix, objKey, exp));
          double angle = calcNumerator(kernelMatrix, objKey, nextKey, exp) / nenner;
          max = Math.max(angle, max);
        }
        if(max < 0.5) {
          expList.add(nextKey);
        }
      }
      explaintab.put(objKey, expList);
    }
    System.out.println("--------------------------------------------");
    System.out.println("Result: ABOD");
    int count = 0;
    while(!pq.isEmpty()) {
      if(count > 10) {
        break;
      }
      double factor = pq.peek().getFirst();
      DBID key = pq.remove().getSecond();
      System.out.print(data.get(key) + " ");
      System.out.println(count + " Factor=" + factor + " " + key);
      LinkedList<DBID> expList = explaintab.get(key);
      generateExplanation(data, key, expList);
      count++;
    }
    System.out.println("--------------------------------------------");
  }

  private void generateExplanation(Relation<V> data, DBID key, LinkedList<DBID> expList) {
    V vect1 = data.get(key);
    Iterator<DBID> iter = expList.iterator();
    while(iter.hasNext()) {
      System.out.println("Outlier: " + vect1);
      V exp = data.get(iter.next());
      System.out.println("Most common neighbor: " + exp);
      // determine difference Vector
      V vals = exp.minus(vect1);
      System.out.println(vals);
      // System.out.println(new FeatureVector(
      // "Diff-"+vect1.getPrimaryKey(),vals ));
    }
    System.out.println();
  }

  /**
   * Run ABOD on the data set
   * 
   * @param database
   * @param relation
   * @return Outlier detection result
   */
  public OutlierResult run(Database database, Relation<V> relation) {
    if(sampleSize > 0) {
      return getFastRanking(relation, k, sampleSize);
    }
    else {
      return getRanking(relation, k);
    }
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<V extends NumberVector<V, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<V, DoubleDistance> {
    protected int k = 0;

    protected int sampleSize = 0;

    protected PrimitiveSimilarityFunction<V, DoubleDistance> primitiveKernelFunction = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final IntParameter kP = new IntParameter(K_ID, new GreaterEqualConstraint(1), 30);
      if(config.grab(kP)) {
        k = kP.getValue();
      }
      final IntParameter sampleSizeP = new IntParameter(FAST_SAMPLE_ID, new GreaterEqualConstraint(1), true);
      if(config.grab(sampleSizeP)) {
        sampleSize = sampleSizeP.getValue();
      }
      final ObjectParameter<PrimitiveSimilarityFunction<V, DoubleDistance>> param = new ObjectParameter<PrimitiveSimilarityFunction<V, DoubleDistance>>(KERNEL_FUNCTION_ID, PrimitiveSimilarityFunction.class, PolynomialKernelFunction.class);
      if(config.grab(param)) {
        primitiveKernelFunction = param.instantiateClass(config);
      }
    }

    @Override
    protected ABOD<V> makeInstance() {
      return new ABOD<V>(k, sampleSize, primitiveKernelFunction, distanceFunction);
    }
  }
}