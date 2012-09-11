package de.lmu.ifi.dbs.elki.utilities;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
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

import gnu.trove.map.hash.TIntDoubleHashMap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.SparseNumberVector;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.ArrayLikeUtil;

/**
 * This class collects various static helper methods.
 * 
 * For helper methods related to special application fields see other utilities
 * classes.
 * 
 * @see de.lmu.ifi.dbs.elki.utilities
 */
public final class Util {
  /**
   * Fake constructor: do not instantiate.
   */
  private Util() {
    // Do not instantiate.
  }

  /**
   * Returns a new double array containing the same objects as are contained in
   * the given array.
   * 
   * @param array an array to copy
   * @return the copied array
   */
  public static double[] copy(double[] array) {
    double[] copy = new double[array.length];
    System.arraycopy(array, 0, copy, 0, array.length);
    return copy;
  }

  /**
   * Returns a new <code>Double</code> array initialized to the values
   * represented by the specified <code>String</code> and separated by comma, as
   * performed by the <code>valueOf</code> method of class <code>Double</code>.
   * 
   * @param s the string to be parsed.
   * @return a new <code>Double</code> array represented by s
   */
  public static double[] parseDoubles(String s) {
    List<Double> result = new ArrayList<Double>();
    StringTokenizer tokenizer = new StringTokenizer(s, ",");
    while (tokenizer.hasMoreTokens()) {
      String d = tokenizer.nextToken();
      result.add(Double.parseDouble(d));
    }
    return ArrayLikeUtil.toPrimitiveDoubleArray(result);
  }

  /**
   * Prints the given list to the specified PrintStream. The list entries are
   * separated by the specified separator. The last entry is not followed by a
   * separator. Thus, if a newline is used as separator, it might make sense to
   * print a newline to the PrintStream after calling this method.
   * 
   * @param <O> object class
   * @param list the list to be printed
   * @param separator the separator to separate entries of the list
   * @param out the target PrintStream
   */
  public static <O> void print(List<O> list, String separator, PrintStream out) {
    for (Iterator<O> iter = list.iterator(); iter.hasNext();) {
      out.print(iter.next());
      if (iter.hasNext()) {
        out.print(separator);
      }
    }
  }

  /**
   * Creates a new BitSet of fixed cardinality with randomly set bits.
   * 
   * @param cardinality the cardinality of the BitSet to create
   * @param capacity the capacity of the BitSet to create - the randomly
   *        generated indices of the bits set to true will be uniformly
   *        distributed between 0 (inclusive) and capacity (exclusive)
   * @param random a Random Object to create the sequence of indices set to true
   *        - the same number occurring twice or more is ignored but the already
   *        selected bit remains true
   * @return a new BitSet with randomly set bits
   */
  public static BitSet randomBitSet(int cardinality, int capacity, Random random) {
    assert (cardinality >= 0) : "Cannot set a negative number of bits!";
    assert (cardinality < capacity) : "Cannot set " + cardinality + " of " + capacity + " bits!";
    BitSet bitset = new BitSet(capacity);
    if (cardinality < capacity >>> 1) {
      while (bitset.cardinality() < cardinality) {
        bitset.set(random.nextInt(capacity));
      }
    } else {
      bitset.flip(0, capacity);
      while (bitset.cardinality() > cardinality) {
        bitset.clear(random.nextInt(capacity));
      }
    }
    return bitset;
  }

  /**
   * Provides a new NumberVector as a projection on the specified attributes.
   * 
   * @param v a NumberVector to project
   * @param selectedAttributes the attributes selected for projection
   * @param factory Vector factory
   * @param <V> Vector type
   * @return a new NumberVector as a projection on the specified attributes
   */
  public static <V extends NumberVector<?>> V project(V v, BitSet selectedAttributes, NumberVector.Factory<V, ?> factory) {
    if (factory instanceof SparseNumberVector.Factory) {
      final SparseNumberVector.Factory<?, ?> sfactory = (SparseNumberVector.Factory<?, ?>) factory;
      TIntDoubleHashMap values = new TIntDoubleHashMap(selectedAttributes.cardinality(), 1);
      for (int d = selectedAttributes.nextSetBit(0); d >= 0; d = selectedAttributes.nextSetBit(d + 1)) {
        if (v.doubleValue(d + 1) != 0.0) {
          values.put(d, v.doubleValue(d + 1));
        }
      }
      // We can't avoid this cast, because Java doesn't know that V is a SparseNumberVector:
      @SuppressWarnings("unchecked")
      V projectedVector = (V) sfactory.newNumberVector(values, selectedAttributes.cardinality());
      return projectedVector;
    } else {
      double[] newAttributes = new double[selectedAttributes.cardinality()];
      int i = 0;
      for (int d = selectedAttributes.nextSetBit(0); d >= 0; d = selectedAttributes.nextSetBit(d + 1)) {
        newAttributes[i] = v.doubleValue(d + 1);
        i++;
      }
      return factory.newNumberVector(newAttributes);
    }
  }

  /**
   * Mix multiple hashcodes into one.
   * 
   * @param hash Hashcodes to mix
   * @return Mixed hash code
   */
  public static int mixHashCodes(int... hash) {
    final long prime = 2654435761L;
    if (hash.length == 0) {
      return 0;
    }
    long result = hash[0];
    for (int i = 1; i < hash.length; i++) {
      result = result * prime + hash[i];
    }
    return (int) result;
  }

  /**
   * Static instance.
   */
  private static final Comparator<?> FORWARD = new ForwardComparator();

  /**
   * Regular comparator. See {@link java.util.Collections#reverseOrder()} for a
   * reverse comparator.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  private static final class ForwardComparator implements Comparator<Comparable<Object>> {
    @Override
    public int compare(Comparable<Object> o1, Comparable<Object> o2) {
      return o1.compareTo(o2);
    }
  }

  /**
   * Compare two objects, forward. See
   * {@link java.util.Collections#reverseOrder()} for a reverse comparator.
   * 
   * @param <T> Object type
   * @return Forward comparator
   */
  @SuppressWarnings("unchecked")
  public static <T> Comparator<T> forwardOrder() {
    return (Comparator<T>) FORWARD;
  }
}
