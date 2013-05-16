package tutorial.clustering;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.SLINK;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * This tutorial will step you through implementing a well known clustering
 * algorithm, agglomerative hierarchical clustering, in multiple steps.
 * 
 * This is the first step, where we implement it with single linkage only, and
 * extract a fixed number of clusters. The follow up variants will be made more
 * flexible.
 * 
 * This is the naive O(n^3) algorithm. See {@link SLINK} for a much faster
 * algorithm (however, only for single-linkage).
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 */
public class NaiveAgglomerativeHierarchicalClustering1<O, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm<O, D, Result> {
  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(NaiveAgglomerativeHierarchicalClustering1.class);

  /**
   * Threshold, how many clusters to extract.
   */
  int numclusters;

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function to use
   * @param numclusters Number of clusters
   */
  public NaiveAgglomerativeHierarchicalClustering1(DistanceFunction<? super O, D> distanceFunction, int numclusters) {
    super(distanceFunction);
    this.numclusters = numclusters;
  }

  /**
   * Run the algorithm
   * 
   * @param db Database
   * @param relation Relation
   * @return Clustering hierarchy
   */
  public Result run(Database db, Relation<O> relation) {
    DistanceQuery<O, D> dq = db.getDistanceQuery(relation, getDistanceFunction());
    ArrayDBIDs ids = DBIDUtil.ensureArray(relation.getDBIDs());
    final int size = ids.size();

    LOG.verbose("Notice: SLINK is a much faster algorithm for single-linkage clustering!");

    // Compute the initial distance matrix.
    double[][] matrix = new double[size][size];
    DBIDArrayIter ix = ids.iter(), iy = ids.iter();
    for (int x = 0; ix.valid(); x++, ix.advance()) {
      iy.seek(0);
      for (int y = 0; y < x; y++, iy.advance()) {
        final double dist = dq.distance(ix, iy).doubleValue();
        matrix[x][y] = dist;
        matrix[y][x] = dist;
      }
    }

    // Initialize space for result:
    double[] height = new double[size];
    Arrays.fill(height, -1.);
    // Parent node, to track merges
    // have every object point to itself initially
    ArrayModifiableDBIDs parent = DBIDUtil.newArray(ids);
    // Active clusters, when not trivial.
    TIntObjectMap<ModifiableDBIDs> clusters = new TIntObjectHashMap<>();

    // Repeat until everything merged, except the desired number of clusters:
    final int stop = size - numclusters;
    FiniteProgress prog = LOG.isVerbose() ? new FiniteProgress("Agglomerative clustering", stop, LOG) : null;
    for (int i = 0; i < stop; i++) {
      double min = Double.POSITIVE_INFINITY;
      int minx = -1, miny = -1;
      for (int x = 0; x < size; x++) {
        if (height[x] > 0) {
          continue;
        }
        for (int y = 0; y < x; y++) {
          if (height[y] > 0) {
            continue;
          }
          if (matrix[x][y] < min) {
            min = matrix[x][y];
            minx = x;
            miny = y;
          }
        }
      }
      assert (minx >= 0 && miny >= 0);
      // Avoid allocating memory, by reusing existing iterators:
      ix.seek(minx);
      iy.seek(miny);
      // Perform merge in data structure: x -> y
      // Since y < x, prefer keeping y, dropping x.
      height[minx] = min;
      parent.set(minx, iy);
      // Merge into cluster
      ModifiableDBIDs cx = clusters.get(minx);
      ModifiableDBIDs cy = clusters.get(miny);
      if (cy == null) {
        cy = DBIDUtil.newHashSet();
        cy.add(iy);
      }
      if (cx == null) {
        cy.add(ix);
      } else {
        cy.addDBIDs(cx);
        clusters.remove(minx);
      }
      clusters.put(miny, cy);
      // Update distance matrix for y:
      for (int j = 0; j < size; j++) {
        matrix[j][miny] = Math.min(matrix[j][minx], matrix[j][miny]);
        matrix[miny][j] = Math.min(matrix[minx][j], matrix[miny][j]);
      }
      if (prog != null) {
        prog.incrementProcessed(LOG);
      }
    }
    if (prog != null) {
      prog.ensureCompleted(LOG);
    }

    // Build the clustering result
    final Clustering<Model> dendrogram = new Clustering<>("Hierarchical-Clustering", "hierarchical-clustering");
    for (int x = 0; x < size; x++) {
      if (height[x] < 0) {
        DBIDs cids = clusters.get(x);
        if (cids == null) {
          ix.seek(x);
          cids = DBIDUtil.deref(ix);
        }
        Cluster<Model> cluster = new Cluster<>("Cluster", cids);
        dendrogram.addToplevelCluster(cluster);
      }
    }
    return dendrogram;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    // The input relation must match our distance function:
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class
   * 
   * @author Erich Schubert
   * 
   * @param <O> Object type
   * @param <D> Distance type
   */
  public static class Parameterizer<O, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<O, D> {
    /**
     * Desired number of clusters.
     */
    int numclusters = 0;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter numclustersP = new IntParameter(SLINK.Parameterizer.SLINK_MINCLUSTERS_ID);
      numclustersP.addConstraint(new GreaterEqualConstraint(1));
      if (config.grab(numclustersP)) {
        numclusters = numclustersP.intValue();
      }
    }

    @Override
    protected NaiveAgglomerativeHierarchicalClustering1<O, D> makeInstance() {
      return new NaiveAgglomerativeHierarchicalClustering1<>(distanceFunction, numclusters);
    }
  }
}