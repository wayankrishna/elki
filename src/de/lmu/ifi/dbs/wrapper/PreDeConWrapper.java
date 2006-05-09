package de.lmu.ifi.dbs.wrapper;

import de.lmu.ifi.dbs.algorithm.KDDTask;
import de.lmu.ifi.dbs.algorithm.clustering.PreDeCon;
import de.lmu.ifi.dbs.logging.LoggingConfiguration;
import de.lmu.ifi.dbs.normalization.AttributeWiseRealVectorNormalization;
import de.lmu.ifi.dbs.utilities.optionhandling.OptionHandler;
import de.lmu.ifi.dbs.utilities.optionhandling.ParameterException;

import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A wrapper for the PreDeCon algorithm. Performs an attribute wise normalization on
 * the database objects.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public class PreDeConWrapper extends FileBasedDatabaseConnectionWrapper {
  /**
   * Holds the class specific debug status.
   */
  @SuppressWarnings({"unused", "UNUSED_SYMBOL"})
  private static final boolean DEBUG = LoggingConfiguration.DEBUG;
//  private static final boolean DEBUG = true;

  /**
   * The logger of this class.
   */
  private Logger logger = Logger.getLogger(this.getClass().getName());

  /**
   * Main method to run this wrapper.
   *
   * @param args the arguments to run this wrapper
   */
  public static void main(String[] args) {
    PreDeConWrapper wrapper = new PreDeConWrapper();
    try {
      wrapper.run(args);
    }
    catch (ParameterException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      wrapper.logger.log(Level.SEVERE, wrapper.optionHandler.usage(e.getMessage()), cause);
    }
  }

  /**
   * Provides a wrapper for the 4C algorithm.
   */
  public PreDeConWrapper() {
    super();
    parameterToDescription.put(PreDeCon.EPSILON_P + OptionHandler.EXPECTS_VALUE, PreDeCon.EPSILON_D);
    parameterToDescription.put(PreDeCon.MINPTS_P + OptionHandler.EXPECTS_VALUE, PreDeCon.MINPTS_D);
    parameterToDescription.put(PreDeCon.LAMBDA_P + OptionHandler.EXPECTS_VALUE, PreDeCon.LAMBDA_D);
    optionHandler = new OptionHandler(parameterToDescription, getClass().getName());
  }

  /**
   * @see de.lmu.ifi.dbs.wrapper.KDDTaskWrapper#getParameters()
   */
  public List<String> getParameters() throws ParameterException {
    List<String> parameters = super.getParameters();

    // PreDeCon algorithm
    parameters.add(OptionHandler.OPTION_PREFIX + KDDTask.ALGORITHM_P);
    parameters.add(PreDeCon.class.getName());

    // epsilon for PreDeCon
    parameters.add(OptionHandler.OPTION_PREFIX + PreDeCon.EPSILON_P);
    parameters.add(optionHandler.getOptionValue(PreDeCon.EPSILON_P));

    // minpts for PreDeCon
    parameters.add(OptionHandler.OPTION_PREFIX + PreDeCon.MINPTS_P);
    parameters.add(optionHandler.getOptionValue(PreDeCon.MINPTS_P));

    // lambda for PreDeCon
    parameters.add(OptionHandler.OPTION_PREFIX + PreDeCon.LAMBDA_P);
    parameters.add(optionHandler.getOptionValue(PreDeCon.LAMBDA_P));

    // normalization
    parameters.add(OptionHandler.OPTION_PREFIX + KDDTask.NORMALIZATION_P);
    parameters.add(AttributeWiseRealVectorNormalization.class.getName());
    parameters.add(OptionHandler.OPTION_PREFIX + KDDTask.NORMALIZATION_UNDO_F);

    return parameters;
  }
}
