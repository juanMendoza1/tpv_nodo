package com.nodo.tpv.data.converters;

import androidx.room.TypeConverter;

import java.math.BigDecimal;

public class BigDecimalConverter {

    @TypeConverter
    public static BigDecimal fromString(String value) {
        if (value == null) return null;
        return new BigDecimal(value);
    }

    @TypeConverter
    public static String bigDecimalToString(BigDecimal bigDecimal) {
        if (bigDecimal == null) return null;
        return bigDecimal.toPlainString();
    }

}
