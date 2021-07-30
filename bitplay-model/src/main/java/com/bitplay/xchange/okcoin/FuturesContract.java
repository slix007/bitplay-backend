package com.bitplay.xchange.okcoin;

/**
 * Delivery dates for future date currencies
 */
public enum FuturesContract {
  ThisWeek("this_week"), NextWeek("next_week"), Month("month"), Quarter("quarter"), BiQuarter("bi_quarter"), Swap("swap");

  private final String name;

  /**
   * Private constructor so it cannot be instantiated
   */
  private FuturesContract(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static <T extends Enum<T>> T valueOfIgnoreCase(Class<T> enumeration, String name) {

    for (T enumValue : enumeration.getEnumConstants()) {
      if (enumValue.name().equalsIgnoreCase(name)) {
        return enumValue;
      }
    }

    throw new IllegalArgumentException(String.format("There is no value with name '%s' in Enum %s", name, enumeration.getName()));
  }
}
