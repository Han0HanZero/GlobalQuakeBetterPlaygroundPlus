package globalquake.ui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 轻量级国际化工具类。
 * 支持运行时切换语言，UI 重建后即可生效。
 * 语言包位于 resources/i18n/messages_*.properties。
 */
public class I18n {

	private static ResourceBundle bundle;
	private static Locale currentLocale = Locale.ENGLISH;

	private I18n() {
	}

	public static void init(Locale locale) {
		setLocale(locale);
	}

	public static void setLocale(Locale locale) {
		currentLocale = locale;
		try {
			bundle = ResourceBundle.getBundle("i18n.messages", locale);
		} catch (MissingResourceException e) {
			bundle = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
		}
	}

	/** 0=简体中文，1=English */
	public static void applyLanguage(int languageIndex) {
		setLocale(languageIndex == 0 ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH);
	}

	public static Locale getLocale() {
		return currentLocale;
	}

	public static String get(String key) {
		try {
			return bundle.getString(key);
		} catch (MissingResourceException | NullPointerException e) {
			return key;
		}
	}

	public static String format(String key, Object... args) {
		return new MessageFormat(get(key), currentLocale).format(args);
	}
}
