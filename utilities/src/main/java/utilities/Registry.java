package utilities;


import com.sun.jna.Memory;
import org.apache.commons.lang3.StringUtils;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Contains methods to interact with the Windows registry.
 * In this version, added support for multi-string type of registry key setting. And support KEY_WOW64_64KEY (8/20/2015)
 * @author mreiter
 */
public class Registry {

	private static final Logger LOGGER = LogManager.getLogger(Registry.class);
	private static Registry registry = new Registry();
	
	/**
	 * Protected constructor.
	 */
	protected Registry() {}
	
	/**
	 * @return The underlying registry implementation.
	 */
	protected static Registry getRegistry() {
		return registry;
	}
	
	/**
	 * @param registry The underlying registry implementation.
	 */
	protected static void setRegistry(Registry registry) {
		Registry.registry = registry;
	}

	/**
	 * Determines whether the specified registry key exists.
	 *
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @return Whether the registry key exists.
	 */
	public static boolean registryKeyExists(HKEY root, String key) {
		return registry.registryKeyExists_Internal(root, key);
	}

	/**
	 * Determines whether the specified registry key exists.
	 *
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @return Whether the registry key exists.
	 */
	protected boolean registryKeyExists_Internal(HKEY root, String key) {
		return Advapi32Util.registryKeyExists(root, key);
	}

	/**
	 * Get the mulit-string (type: REG_MULTI_SZ)value from registry
	 * @param root The registry root
	 * @param key  the path to the registry key
	 * @param setting The name of the setting to retrieve
	 * @return  if successfully, return the array of String, otherwise return null
	 */
	public static String[] getMultiStringValue(HKEY root, String key, String setting) {
		return getMultiStringValue(root, key, setting, null);
	}

	public static String[] getMultiStringValue(HKEY root, String key, String setting, String[] defaultValues) {
		return registry.getMultiStringValue_Internal(root, key, setting, defaultValues);
	}

	protected String[] getMultiStringValue_Internal(HKEY root, String key, String setting, String[] defaultValues) {
		try {
			return registryGetMultiStringValue(root, key, setting, defaultValues);
		} catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}

		return defaultValues;
	}

	private static String[] registryGetMultiStringValue(HKEY root, String key, String setting, String[] defaultValues) {
		HKEYByReference phkKey = new HKEYByReference();
		int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ|WinNT.KEY_WOW64_64KEY, phkKey);
		if (rc != W32Errors.ERROR_SUCCESS) {
			if (rc != W32Errors.ERROR_FILE_NOT_FOUND) { // Only log unexpected errors
				LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + key + ": could not open registry key; RC=" + rc);
			}
			return defaultValues;
		}

		try {
			return registryGetMultiStringValue(root, phkKey.getValue(), key, setting, defaultValues);
		} finally {
			rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
			if (rc != W32Errors.ERROR_SUCCESS)
				LOGGER.info("Error closing registry key " + key);
		}
	}

    @SuppressWarnings("all")
	private static String[] registryGetMultiStringValue(HKEY root, HKEY key, String keyName, String setting, String[] defaultValues) {

		IntByReference lpcbData = new IntByReference();
		IntByReference lpType = new IntByReference();
		int rc = Advapi32.INSTANCE.RegQueryValueEx(key, setting, 0, lpType, (char[]) null, lpcbData);
		if (rc == W32Errors.ERROR_SUCCESS || rc == W32Errors.ERROR_INSUFFICIENT_BUFFER) {
			if (lpType.getValue() != WinNT.REG_MULTI_SZ) {
				LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + keyName + ": Unsupported type: " + lpType.getValue());
				return defaultValues;
			}
			Memory data = new Memory((long) lpcbData.getValue());
			rc = Advapi32.INSTANCE.RegQueryValueEx(key, setting, 0, lpType, data, lpcbData);
			if (rc == W32Errors.ERROR_SUCCESS || rc == W32Errors.ERROR_INSUFFICIENT_BUFFER) {
				ArrayList result = new ArrayList();
				int offset = 0;
				while ((long) offset < data.size()) {
					String s = data.getString((long) offset, true);
					offset += s.length() * Native.WCHAR_SIZE;
					offset += Native.WCHAR_SIZE;
					result.add(s);
				}
				String [] values  = (String[]) result.toArray(new String[0]);
				return values;
			}
		}
		if (rc != W32Errors.ERROR_FILE_NOT_FOUND) { // Only log unexpected errors
			LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + keyName + "; RC=" + rc);
		}
		return defaultValues;
	}

	/**
	 * Set multi-string value from registry key
	 * @param root  The regsitry root
	 * @param key   The path to the registry key
	 * @param setting  The name of setting to modify
	 * @param values   The new value of the setting which is the array of String
	 * @return true if successfully reset the value, false otherwise.
	 */
	public static boolean setMultiStringValue(HKEY root, String key, String setting, String[] values) {
		return registry.setMultiStringValue_Internal(root, key, setting, values);
	}

	protected boolean setMultiStringValue_Internal(HKEY root, String key, String setting, String[] values)
	{
		return registrySetMultiStringValue(root, key, setting, values);
	}

	private static boolean registrySetMultiStringValue(HKEY root, String key, String setting, String[] values) {

		HKEYByReference phkKey = new HKEYByReference();
		int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, (WinNT.KEY_READ | WinNT.KEY_WRITE | WinNT.KEY_WOW64_64KEY), phkKey);
		if (rc != W32Errors.ERROR_SUCCESS) {
			LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + key + "; RC=" + rc);
			return false;
		}
		try {
			Advapi32Util.registrySetStringArray(phkKey.getValue(), setting, values);
			return true;
		}
		catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}
		catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}
		catch (Exception e) {
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}
		finally {
			rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
			if (rc != W32Errors.ERROR_SUCCESS) {
				LOGGER.info("Error closing registry key " + key);
			}
		}
		return false;
	}

	/**
	 * Gets a string value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @return The value of the setting.
	 */
	public static String getStringValue(HKEY root, String key, String setting) {
		return getStringValue(root, key, setting, "");
	}
	
	/**
	 * Gets a string value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param defaultValue The value to return if the setting does not exist.
	 * @return The value of the setting.
	 */
	public static String getStringValue(HKEY root, String key, String setting, String defaultValue) {
		return registry.getStringValue_Internal(root, key, setting, defaultValue);
	}
	
	/**
	 * Gets a value from the registry as a string.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param defaultValue The value to return if the setting does not exist.
	 * @return The value of the setting.
	 */
	protected String getStringValue_Internal(HKEY root, String key, String setting, String defaultValue) {
		try {
			return registryGetStringValue(root, key, setting, defaultValue);
		} catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (Exception e) {
			LOGGER.info("Caught Exception: Error getting " + setting + " from HKLM\\" + key, e);
		}
		
		return defaultValue;
	}
	
	/**
	 * Gets a value from the registry as a string.
	 * <p>
	 * Based on Advapi32Util.registryGetStringValue and Advapi32Util.registryGetIntValue.
	 * </p>
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param defaultValue The value to return if the setting does not exist.
	 * @return The value of the setting.
	 */
	private static String registryGetStringValue(HKEY root, String key, String setting, String defaultValue) {
		HKEYByReference phkKey = new HKEYByReference();
		int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ|WinNT.KEY_WOW64_64KEY, phkKey);
		if (rc != W32Errors.ERROR_SUCCESS) {
			if (rc != W32Errors.ERROR_FILE_NOT_FOUND) { // Only log unexpected errors
				LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + key + ": could not open registry key; RC=" + rc);
			}

			LOGGER.info("getting " + setting + " from " + getRootName(root) + "\\" + key + ": RC=" + rc);

			return defaultValue;
		}
		
		try {
			return registryGetStringValue(root, phkKey.getValue(), key, setting, defaultValue);
		} finally {
			rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
			if (rc != W32Errors.ERROR_SUCCESS)
				LOGGER.info("Error closing registry key " + key);
		}
	}
	
	/**
	 * Gets a value from the registry as a string.
	 * <p>
	 * Based on Advapi32Util.registryGetStringValue and Advapi32Util.registryGetIntValue.
	 * </p>
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param defaultValue The value to return if the setting does not exist.
	 * @return The value of the setting.
	 */
	private static String registryGetStringValue(HKEY root, HKEY key, String keyName, String setting, String defaultValue) {
		// Query the registry setting to get its length and type
		IntByReference lpcbData = new IntByReference();
		IntByReference lpType = new IntByReference();
		int rc = Advapi32.INSTANCE.RegQueryValueEx(key, setting, 0, lpType, (char[]) null, lpcbData);
		if (rc == W32Errors.ERROR_SUCCESS || rc == W32Errors.ERROR_INSUFFICIENT_BUFFER) {
			// Get the value of the registry setting and return it
			switch (lpType.getValue()) {
				case WinNT.REG_DWORD:
				{
					IntByReference data = new IntByReference();
					rc = Advapi32.INSTANCE.RegQueryValueEx(key, setting, 0, lpType, data, lpcbData);
					if (rc == W32Errors.ERROR_SUCCESS)
						return String.valueOf(data.getValue());
					
					break;
				}
				case WinNT.REG_SZ:
				case WinNT.REG_EXPAND_SZ:
				{
					char[] data = new char[lpcbData.getValue()];
					rc = Advapi32.INSTANCE.RegQueryValueEx(key, setting, 0, lpType, data, lpcbData);
					if (rc == W32Errors.ERROR_SUCCESS)
						return Native.toString(data);
					
					break;
				}
				default:
					LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + keyName + ": Unsupported type: " + lpType.getValue());
					return defaultValue;
			}
		}

		if (rc != W32Errors.ERROR_FILE_NOT_FOUND) { // Only log unexpected errors
			LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + keyName + "; RC=" + rc);
		}

		return defaultValue;
	}
	
	private static String getRootName(HKEY root) {
		if (root == WinReg.HKEY_LOCAL_MACHINE)
			return "HKLM";
		else if (root == WinReg.HKEY_CURRENT_USER)
			return "HKCU";
		else
			return "";
	}

	/**
	 * Gets an integer value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @return The value of the setting.
	 */
	public static int getIntValue(HKEY root, String key, String setting) {
		return getIntValue(root, key, setting, 0);
	}
	
	/**
	 * Gets an integer value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param defaultValue The value to return if the setting does not exist or is not a valid integer.
	 * @return The value of the setting.
	 */
	public static int getIntValue(HKEY root, String key, String setting, int defaultValue) {
		int intValue = defaultValue;

		try {
			String valueStr = getStringValue(root, key, setting);
			if (!StringUtils.isBlank(valueStr)) {
				intValue = Integer.parseInt(valueStr);
			}
		} catch (Exception ignore) {}

		return intValue;
	}

	/**
	 * Gets the names of sub-keys of the given key.
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @return The names of sub-keys of the given key.
	 */
	public static List<String> getSubKeyNames(HKEY root, String key) {
		return registry.getSubKeyNames_Internal(root, key);
	}

	protected List<String> getSubKeyNames_Internal(HKEY root, String key) {
		try {
			return Arrays.asList(Advapi32Util.registryGetKeys(root, key));
		} catch (Exception ignore) {
			return Collections.emptyList();
		}
	}

	/**
	 * Sets a string value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param value The value of the setting.
	 */
	public static boolean setStringValue(HKEY root, String key, String setting, String value) {
		return registry.setStringValue_Internal(root, key, setting, value);
	}
	
	/**
	 * Sets an int value from the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param value The value of the setting.
	 */
	public static boolean setIntValue(HKEY root, String key, String setting, int value) {
		return registry.setIntValue_Internal(root, key, setting, value);
	}
	
	protected boolean setStringValue_Internal(HKEY root, String key, String setting, String value) 
	{
		return registrySetStringValue(root, key, setting, value);
	}
	protected boolean setIntValue_Internal(HKEY root, String key, String setting, int value)
	{
		return registrySetIntValue(root, key, setting, value);
	}
	
	/**
	 * Sets a string value in the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param value The value of the setting.
	 */
	private static boolean registrySetStringValue(HKEY root, String key, String setting, String value) {

        HKEYByReference phkKey = new HKEYByReference();
        int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, (WinNT.KEY_READ | WinNT.KEY_WRITE | WinNT.KEY_WOW64_64KEY), phkKey);
        if (rc != W32Errors.ERROR_SUCCESS) {
            LOGGER.info("Error getting " + setting + " from " + getRootName(root) + "\\" + key + "; RC=" + rc);
            return false;
        }
        try {
            Advapi32Util.registrySetStringValue(phkKey.getValue(), setting, value);
			return true;
		} catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
        } catch (Exception e) { // Also because there's no registry in Linux
            LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
        }
        finally {
            rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
            if (rc != W32Errors.ERROR_SUCCESS) {
                LOGGER.info("Error closing registry key " + key);
            }
        }
        return false;
	}

	/**
	 * Sets an int value in the registry.
	 * 
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param value The value of the setting.
	 */
	private static boolean registrySetIntValue(HKEY root, String key, String setting, int value) {
		try {
			Advapi32Util.registrySetIntValue(root, key, setting, value);
			return true;
		} catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}
		return false;
	}

	/**
	 * Sets an int value in the registry.
	 *
	 * @param root The registry root.
	 * @param key The path to the registry key.
	 * @param setting The name of the setting to retrieve.
	 * @param value The value of the setting.
	 */
	public static boolean registrySetBinaryValue(HKEY root, String key, String setting, byte[] value) {
		try {
			Advapi32Util.registrySetBinaryValue(root, key, setting, value);
			return true;
		} catch (UnsatisfiedLinkError e) { // There's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		} catch (NoClassDefFoundError e) { // Also because there's no registry in Linux
			LOGGER.info("Error getting " + setting + " from HKLM\\" + key, e);
		}
		return false;
	}
}