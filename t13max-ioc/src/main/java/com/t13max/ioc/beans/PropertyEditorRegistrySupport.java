package com.t13max.ioc.beans;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.utils.ClassUtils;
import org.xml.sax.InputSource;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @Author: t13max
 * @Since: 22:28 2026/1/16
 */
public class PropertyEditorRegistrySupport implements PropertyEditorRegistry {

    private  ConversionService conversionService;

    private boolean defaultEditorsActive = false;

    private boolean configValueEditorsActive = false;

    private  PropertyEditorRegistrar defaultEditorRegistrar;

    @SuppressWarnings("NullAway.Init")
    private Map<Class<?>, PropertyEditor> defaultEditors;

    private  Map<Class<?>, PropertyEditor> overriddenDefaultEditors;

    private  Map<Class<?>, PropertyEditor> customEditors;

    private  Map<String, CustomEditorHolder> customEditorsForPath;

    private  Map<Class<?>, PropertyEditor> customEditorCache;

    
    public void setConversionService( ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    public  ConversionService getConversionService() {
        return this.conversionService;
    }


    //---------------------------------------------------------------------
    // Management of default editors
    //---------------------------------------------------------------------
    
    protected void registerDefaultEditors() {
        this.defaultEditorsActive = true;
    }
    
    public void useConfigValueEditors() {
        this.configValueEditorsActive = true;
    }
    
    public void setDefaultEditorRegistrar(PropertyEditorRegistrar registrar) {
        this.defaultEditorRegistrar = registrar;
    }
    
    public void overrideDefaultEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        if (this.overriddenDefaultEditors == null) {
            this.overriddenDefaultEditors = new HashMap<>();
        }
        this.overriddenDefaultEditors.put(requiredType, propertyEditor);
    }
    
    public  PropertyEditor getDefaultEditor(Class<?> requiredType) {
        if (!this.defaultEditorsActive) {
            return null;
        }
        if (this.overriddenDefaultEditors == null && this.defaultEditorRegistrar != null) {
            this.defaultEditorRegistrar.registerCustomEditors(this);
        }
        if (this.overriddenDefaultEditors != null) {
            PropertyEditor editor = this.overriddenDefaultEditors.get(requiredType);
            if (editor != null) {
                return editor;
            }
        }
        if (this.defaultEditors == null) {
            createDefaultEditors();
        }
        return this.defaultEditors.get(requiredType);
    }
    
    private void createDefaultEditors() {
        this.defaultEditors = new HashMap<>(64);

        // Simple editors, without parameterization capabilities.
        // The JDK does not contain a default editor for any of these target types.
        this.defaultEditors.put(Charset.class, new CharsetEditor());
        this.defaultEditors.put(Class.class, new ClassEditor());
        this.defaultEditors.put(Class[].class, new ClassArrayEditor());
        this.defaultEditors.put(Currency.class, new CurrencyEditor());
        this.defaultEditors.put(File.class, new FileEditor());
        this.defaultEditors.put(InputStream.class, new InputStreamEditor());
        this.defaultEditors.put(InputSource.class, new InputSourceEditor());
        this.defaultEditors.put(Locale.class, new LocaleEditor());
        this.defaultEditors.put(Path.class, new PathEditor());
        this.defaultEditors.put(Pattern.class, new PatternEditor());
        this.defaultEditors.put(Properties.class, new PropertiesEditor());
        this.defaultEditors.put(Reader.class, new ReaderEditor());
        this.defaultEditors.put(Resource[].class, new ResourceArrayPropertyEditor());
        this.defaultEditors.put(TimeZone.class, new TimeZoneEditor());
        this.defaultEditors.put(URI.class, new URIEditor());
        this.defaultEditors.put(URL.class, new URLEditor());
        this.defaultEditors.put(UUID.class, new UUIDEditor());
        this.defaultEditors.put(ZoneId.class, new ZoneIdEditor());

        // Default instances of collection editors.
        // Can be overridden by registering custom instances of those as custom editors.
        this.defaultEditors.put(Collection.class, new CustomCollectionEditor(Collection.class));
        this.defaultEditors.put(Set.class, new CustomCollectionEditor(Set.class));
        this.defaultEditors.put(SortedSet.class, new CustomCollectionEditor(SortedSet.class));
        this.defaultEditors.put(List.class, new CustomCollectionEditor(List.class));
        this.defaultEditors.put(SortedMap.class, new CustomMapEditor(SortedMap.class));

        // Default editors for primitive arrays.
        this.defaultEditors.put(byte[].class, new ByteArrayPropertyEditor());
        this.defaultEditors.put(char[].class, new CharArrayPropertyEditor());

        // The JDK does not contain a default editor for char!
        this.defaultEditors.put(char.class, new CharacterEditor(false));
        this.defaultEditors.put(Character.class, new CharacterEditor(true));

        // Spring's CustomBooleanEditor accepts more flag values than the JDK's default editor.
        this.defaultEditors.put(boolean.class, new CustomBooleanEditor(false));
        this.defaultEditors.put(Boolean.class, new CustomBooleanEditor(true));

        // The JDK does not contain default editors for number wrapper types!
        // Override JDK primitive number editors with our own CustomNumberEditor.
        this.defaultEditors.put(byte.class, new CustomNumberEditor(Byte.class, false));
        this.defaultEditors.put(Byte.class, new CustomNumberEditor(Byte.class, true));
        this.defaultEditors.put(short.class, new CustomNumberEditor(Short.class, false));
        this.defaultEditors.put(Short.class, new CustomNumberEditor(Short.class, true));
        this.defaultEditors.put(int.class, new CustomNumberEditor(Integer.class, false));
        this.defaultEditors.put(Integer.class, new CustomNumberEditor(Integer.class, true));
        this.defaultEditors.put(long.class, new CustomNumberEditor(Long.class, false));
        this.defaultEditors.put(Long.class, new CustomNumberEditor(Long.class, true));
        this.defaultEditors.put(float.class, new CustomNumberEditor(Float.class, false));
        this.defaultEditors.put(Float.class, new CustomNumberEditor(Float.class, true));
        this.defaultEditors.put(double.class, new CustomNumberEditor(Double.class, false));
        this.defaultEditors.put(Double.class, new CustomNumberEditor(Double.class, true));
        this.defaultEditors.put(BigDecimal.class, new CustomNumberEditor(BigDecimal.class, true));
        this.defaultEditors.put(BigInteger.class, new CustomNumberEditor(BigInteger.class, true));

        // Only register config value editors if explicitly requested.
        if (this.configValueEditorsActive) {
            StringArrayPropertyEditor sae = new StringArrayPropertyEditor();
            this.defaultEditors.put(String[].class, sae);
            this.defaultEditors.put(short[].class, sae);
            this.defaultEditors.put(int[].class, sae);
            this.defaultEditors.put(long[].class, sae);
        }
    }
    
    protected void copyDefaultEditorsTo(PropertyEditorRegistrySupport target) {
        target.defaultEditorsActive = this.defaultEditorsActive;
        target.configValueEditorsActive = this.configValueEditorsActive;
        target.defaultEditors = this.defaultEditors;
        target.overriddenDefaultEditors = this.overriddenDefaultEditors;
    }


    //---------------------------------------------------------------------
    // Management of custom editors
    //---------------------------------------------------------------------

    @Override
    public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        registerCustomEditor(requiredType, null, propertyEditor);
    }

    @Override
    public void registerCustomEditor( Class<?> requiredType,  String propertyPath, PropertyEditor propertyEditor) {
        if (requiredType == null && propertyPath == null) {
            throw new IllegalArgumentException("Either requiredType or propertyPath is required");
        }
        if (propertyPath != null) {
            if (this.customEditorsForPath == null) {
                this.customEditorsForPath = new LinkedHashMap<>(16);
            }
            this.customEditorsForPath.put(propertyPath, new CustomEditorHolder(propertyEditor, requiredType));
        }
        else {
            if (this.customEditors == null) {
                this.customEditors = new LinkedHashMap<>(16);
            }
            this.customEditors.put(requiredType, propertyEditor);
            this.customEditorCache = null;
        }
    }

    @Override
    public  PropertyEditor findCustomEditor( Class<?> requiredType,  String propertyPath) {
        Class<?> requiredTypeToUse = requiredType;
        if (propertyPath != null) {
            if (this.customEditorsForPath != null) {
                // Check property-specific editor first.
                PropertyEditor editor = getCustomEditor(propertyPath, requiredType);
                if (editor == null) {
                    List<String> strippedPaths = new ArrayList<>();
                    addStrippedPropertyPaths(strippedPaths, "", propertyPath);
                    for (Iterator<String> it = strippedPaths.iterator(); it.hasNext() && editor == null;) {
                        String strippedPath = it.next();
                        editor = getCustomEditor(strippedPath, requiredType);
                    }
                }
                if (editor != null) {
                    return editor;
                }
            }
            if (requiredType == null) {
                requiredTypeToUse = getPropertyType(propertyPath);
            }
        }
        // No property-specific editor -> check type-specific editor.
        return getCustomEditor(requiredTypeToUse);
    }
    
    public boolean hasCustomEditorForElement( Class<?> elementType,  String propertyPath) {
        if (propertyPath != null && this.customEditorsForPath != null) {
            for (Map.Entry<String, CustomEditorHolder> entry : this.customEditorsForPath.entrySet()) {
                if (PropertyAccessorUtils.matchesProperty(entry.getKey(), propertyPath) &&
                        entry.getValue().getPropertyEditor(elementType) != null) {
                    return true;
                }
            }
        }
        // No property-specific editor -> check type-specific editor.
        return (elementType != null && this.customEditors != null && this.customEditors.containsKey(elementType));
    }
    
    protected  Class<?> getPropertyType(String propertyPath) {
        return null;
    }
    
    private  PropertyEditor getCustomEditor(String propertyName,  Class<?> requiredType) {
        CustomEditorHolder holder =
                (this.customEditorsForPath != null ? this.customEditorsForPath.get(propertyName) : null);
        return (holder != null ? holder.getPropertyEditor(requiredType) : null);
    }
    
    private  PropertyEditor getCustomEditor( Class<?> requiredType) {
        if (requiredType == null || this.customEditors == null) {
            return null;
        }
        // Check directly registered editor for type.
        PropertyEditor editor = this.customEditors.get(requiredType);
        if (editor == null) {
            // Check cached editor for type, registered for superclass or interface.
            if (this.customEditorCache != null) {
                editor = this.customEditorCache.get(requiredType);
            }
            if (editor == null) {
                // Find editor for superclass or interface.
                for (Map.Entry<Class<?>, PropertyEditor> entry : this.customEditors.entrySet()) {
                    Class<?> key = entry.getKey();
                    if (key.isAssignableFrom(requiredType)) {
                        editor = entry.getValue();
                        // Cache editor for search type, to avoid the overhead
                        // of repeated assignable-from checks.
                        if (this.customEditorCache == null) {
                            this.customEditorCache = new HashMap<>();
                        }
                        this.customEditorCache.put(requiredType, editor);
                        if (editor != null) {
                            break;
                        }
                    }
                }
            }
        }
        return editor;
    }
    
    protected  Class<?> guessPropertyTypeFromEditors(String propertyName) {
        if (this.customEditorsForPath != null) {
            CustomEditorHolder editorHolder = this.customEditorsForPath.get(propertyName);
            if (editorHolder == null) {
                List<String> strippedPaths = new ArrayList<>();
                addStrippedPropertyPaths(strippedPaths, "", propertyName);
                for (Iterator<String> it = strippedPaths.iterator(); it.hasNext() && editorHolder == null;) {
                    String strippedName = it.next();
                    editorHolder = this.customEditorsForPath.get(strippedName);
                }
            }
            if (editorHolder != null) {
                return editorHolder.getRegisteredType();
            }
        }
        return null;
    }
    
    protected void copyCustomEditorsTo(PropertyEditorRegistry target,  String nestedProperty) {
        String actualPropertyName =
                (nestedProperty != null ? PropertyAccessorUtils.getPropertyName(nestedProperty) : null);
        if (this.customEditors != null) {
            this.customEditors.forEach(target::registerCustomEditor);
        }
        if (this.customEditorsForPath != null) {
            this.customEditorsForPath.forEach((editorPath, editorHolder) -> {
                if (nestedProperty != null) {
                    int pos = PropertyAccessorUtils.getFirstNestedPropertySeparatorIndex(editorPath);
                    if (pos != -1) {
                        String editorNestedProperty = editorPath.substring(0, pos);
                        String editorNestedPath = editorPath.substring(pos + 1);
                        if (editorNestedProperty.equals(nestedProperty) || editorNestedProperty.equals(actualPropertyName)) {
                            target.registerCustomEditor(
                                    editorHolder.getRegisteredType(), editorNestedPath, editorHolder.getPropertyEditor());
                        }
                    }
                }
                else {
                    target.registerCustomEditor(
                            editorHolder.getRegisteredType(), editorPath, editorHolder.getPropertyEditor());
                }
            });
        }
    }

    
    private void addStrippedPropertyPaths(List<String> strippedPaths, String nestedPath, String propertyPath) {
        int startIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_PREFIX_CHAR);
        if (startIndex != -1) {
            int endIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_SUFFIX_CHAR);
            if (endIndex != -1) {
                String prefix = propertyPath.substring(0, startIndex);
                String key = propertyPath.substring(startIndex, endIndex + 1);
                String suffix = propertyPath.substring(endIndex + 1);
                // Strip the first key.
                strippedPaths.add(nestedPath + prefix + suffix);
                // Search for further keys to strip, with the first key stripped.
                addStrippedPropertyPaths(strippedPaths, nestedPath + prefix, suffix);
                // Search for further keys to strip, with the first key not stripped.
                addStrippedPropertyPaths(strippedPaths, nestedPath + prefix + key, suffix);
            }
        }
    }

    
    private static final class CustomEditorHolder {

        private final PropertyEditor propertyEditor;

        private final  Class<?> registeredType;

        private CustomEditorHolder(PropertyEditor propertyEditor,  Class<?> registeredType) {
            this.propertyEditor = propertyEditor;
            this.registeredType = registeredType;
        }

        private PropertyEditor getPropertyEditor() {
            return this.propertyEditor;
        }

        private  Class<?> getRegisteredType() {
            return this.registeredType;
        }

        private  PropertyEditor getPropertyEditor( Class<?> requiredType) {
            // Special case: If no required type specified, which usually only happens for
            // Collection elements, or required type is not assignable to registered type,
            // which usually only happens for generic properties of type Object -
            // then return PropertyEditor if not registered for Collection or array type.
            // (If not registered for Collection or array, it is assumed to be intended
            // for elements.)
            if (this.registeredType == null ||
                    (requiredType != null &&
                            (ClassUtils.isAssignable(this.registeredType, requiredType) ||
                                    ClassUtils.isAssignable(requiredType, this.registeredType))) ||
                    (requiredType == null &&
                            (!Collection.class.isAssignableFrom(this.registeredType) && !this.registeredType.isArray()))) {
                return this.propertyEditor;
            }
            else {
                return null;
            }
        }
    }
}
