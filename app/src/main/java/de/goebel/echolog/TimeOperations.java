 
package de.goebel.echolog;
 
import android.os.Build;
import android.util.Log;
 
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
 
public class TimeOperations {
    static final String DATE_FORMAT = "yyyy-MM-dd";
    static final String TIME_FORMAT = "HH:mm:ss";
    static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
 
    public static DateTimeFormatter getDateFormatter() {
        return DateTimeFormatter.ofPattern(DATE_FORMAT);
    }
    public static DateTimeFormatter getTimeFormatter() {
        return DateTimeFormatter.ofPattern(TIME_FORMAT);
    }
    public static DateTimeFormatter getDateTimeFormatter() {
        return DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    }
    public static LocalDate getLocalDate(String dateText) {
        try {
            return LocalDate.parse(dateText, getDateFormatter());
        } catch (Exception e) {
            Log.e("EchoLog", "TimeOperations.getLocalDate: " + dateText + " has the wrong format. The needed format is: yyyy-MM-dd", e);
            return null;
        }
    }
    public static LocalTime getLocalTime(String timeText) {
        try {
            return LocalTime.parse(timeText, getTimeFormatter());
        } catch (Exception e) {
            Log.e("EchoLog", "TimeOperations.getLocalTime: " + timeText + " has the wrong format. The needed format is: HH:mm:ss", e);
            return null;
        }
    }
    public static LocalDateTime getLocalDateTime(String dateTimeText) {
        try {
            return LocalDateTime.parse(dateTimeText, getDateTimeFormatter());
        } catch (Exception e) {
            Log.e("EchoLog", "TimeOperations.getLocalDateTime: " + dateTimeText + " has the wrong format. The needed format is: yyyy-MM-dd'T'HH:mm:ss", e);
            return null;
        }
    }
    public static boolean isLaterDay(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getLocalDate(a).isAfter(getLocalDate(b));
        } else {
            Log.e("EchoLog", "TimeOperations.isLaterDay(" + a + ", " + b + ") => There was a error while Parsing, returning false.");
            return false;
        }
    }
 
    public static String getCurrentDayOfWeek() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        switch (day) {
            case MONDAY:    return "Mo";
            case TUESDAY:   return "Tu";
            case WEDNESDAY: return "We";
            case THURSDAY:  return "Th";
            case FRIDAY:    return "Fr";
            case SATURDAY:  return "Sa";
            case SUNDAY:    return "Su";
            default:        return "??";
        }
    }
 
 
}
 
 
 
 
 
 
