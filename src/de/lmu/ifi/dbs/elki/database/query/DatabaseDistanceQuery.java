package de.lmu.ifi.dbs.elki.database.query;

import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DatabaseDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;

/**
 * Run a database query in a database context.
 * 
 * @author Erich Schubert
 *
 * @param <O> Database object type.
 * @param <D> Distance result type.
 */
public class DatabaseDistanceQuery<O extends DatabaseObject, D extends Distance<D>> extends AbstractDatabaseDistanceQuery<O, D> implements Cloneable {
  /**
   * The distance function we use.
   */
  final protected DatabaseDistanceFunction<D> distanceFunction;

  /**
   * Constructor.
   * 
   * @param database Database to use.
   * @param distanceFunction Our distance function
   */
  public DatabaseDistanceQuery(Database<O> database, DatabaseDistanceFunction<D> distanceFunction) {
    super(database);
    this.distanceFunction = distanceFunction;
  }

  @Override
  public D distance(DBID id1, DBID id2) {
    if (id1 == null) {
      throw new UnsupportedOperationException("This distance function can only be used for objects stored in the database.");
    }
    if (id2 == null) {
      throw new UnsupportedOperationException("This distance function can only be used for objects stored in the database.");
    }
    return distanceFunction.distance(id1, id2);
  }

  @Override
  public DatabaseDistanceFunction<D> getDistanceFunction() {
    return distanceFunction;
  }
}