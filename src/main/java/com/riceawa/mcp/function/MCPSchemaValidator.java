package com.riceawa.mcp.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashSet;
import java.util.Set;

/**
 * MCP Schema验证器
 * 用于验证函数参数是否符合MCP工具的输入schema
 */
public class MCPSchemaValidator {
    
    /**
     * 验证参数是否符合schema
     * @param arguments 要验证的参数
     * @param schema JSON Schema定义
     * @return 验证是否通过
     */
    public static boolean validateParameters(JsonObject arguments, JsonObject schema) {
        if (schema == null) {
            // 如果没有schema，认为验证通过
            return true;
        }
        
        if (arguments == null) {
            arguments = new JsonObject();
        }
        
        try {
            return validateObject(arguments, schema);
        } catch (Exception e) {
            System.err.println("Schema验证异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证对象类型
     */
    private static boolean validateObject(JsonObject obj, JsonObject schema) {
        // 检查类型
        JsonElement typeElement = schema.get("type");
        if (typeElement != null && !typeElement.getAsString().equals("object")) {
            return false;
        }
        
        // 验证必需字段
        if (!validateRequiredFields(obj, schema)) {
            return false;
        }
        
        // 验证属性
        JsonElement propertiesElement = schema.get("properties");
        if (propertiesElement != null && propertiesElement.isJsonObject()) {
            JsonObject properties = propertiesElement.getAsJsonObject();
            
            for (String propertyName : properties.keySet()) {
                JsonElement propertySchema = properties.get(propertyName);
                JsonElement propertyValue = obj.get(propertyName);
                
                if (propertyValue != null && !validateValue(propertyValue, propertySchema)) {
                    return false;
                }
            }
        }
        
        // 验证额外属性
        JsonElement additionalPropertiesElement = schema.get("additionalProperties");
        if (additionalPropertiesElement != null && additionalPropertiesElement.isJsonPrimitive() 
            && !additionalPropertiesElement.getAsBoolean()) {
            
            // 不允许额外属性
            Set<String> allowedProperties = new HashSet<>();
            if (propertiesElement != null && propertiesElement.isJsonObject()) {
                for (String key : propertiesElement.getAsJsonObject().keySet()) {
                    allowedProperties.add(key);
                }
            }
            
            for (String key : obj.keySet()) {
                if (!allowedProperties.contains(key)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 验证必需字段
     */
    private static boolean validateRequiredFields(JsonObject obj, JsonObject schema) {
        JsonElement requiredElement = schema.get("required");
        if (requiredElement == null || !requiredElement.isJsonArray()) {
            return true;
        }
        
        JsonArray requiredFields = requiredElement.getAsJsonArray();
        for (JsonElement fieldElement : requiredFields) {
            if (fieldElement.isJsonPrimitive()) {
                String fieldName = fieldElement.getAsString();
                if (!obj.has(fieldName) || obj.get(fieldName).isJsonNull()) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 验证单个值
     */
    private static boolean validateValue(JsonElement value, JsonElement schema) {
        if (!schema.isJsonObject()) {
            return true; // 如果schema不是对象，默认通过
        }
        
        JsonObject schemaObj = schema.getAsJsonObject();
        
        // 检查类型
        JsonElement typeElement = schemaObj.get("type");
        if (typeElement != null && typeElement.isJsonPrimitive()) {
            String expectedType = typeElement.getAsString();
            if (!validateType(value, expectedType)) {
                return false;
            }
        }
        
        // 根据类型进行具体验证
        if (value.isJsonObject() && "object".equals(getTypeFromSchema(schemaObj))) {
            return validateObject(value.getAsJsonObject(), schemaObj);
        } else if (value.isJsonArray() && "array".equals(getTypeFromSchema(schemaObj))) {
            return validateArray(value.getAsJsonArray(), schemaObj);
        } else if (value.isJsonPrimitive()) {
            return validatePrimitive(value.getAsJsonPrimitive(), schemaObj);
        }
        
        return true;
    }
    
    /**
     * 验证数组类型
     */
    private static boolean validateArray(JsonArray array, JsonObject schema) {
        // 验证数组长度
        JsonElement minItemsElement = schema.get("minItems");
        if (minItemsElement != null && minItemsElement.isJsonPrimitive()) {
            int minItems = minItemsElement.getAsInt();
            if (array.size() < minItems) {
                return false;
            }
        }
        
        JsonElement maxItemsElement = schema.get("maxItems");
        if (maxItemsElement != null && maxItemsElement.isJsonPrimitive()) {
            int maxItems = maxItemsElement.getAsInt();
            if (array.size() > maxItems) {
                return false;
            }
        }
        
        // 验证数组项
        JsonElement itemsElement = schema.get("items");
        if (itemsElement != null) {
            for (JsonElement item : array) {
                if (!validateValue(item, itemsElement)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 验证基本类型
     */
    private static boolean validatePrimitive(JsonPrimitive primitive, JsonObject schema) {
        String type = getTypeFromSchema(schema);
        
        if ("string".equals(type)) {
            if (!primitive.isString()) {
                return false;
            }
            return validateString(primitive.getAsString(), schema);
        } else if ("number".equals(type) || "integer".equals(type)) {
            if (!primitive.isNumber()) {
                return false;
            }
            return validateNumber(primitive.getAsNumber(), schema, "integer".equals(type));
        } else if ("boolean".equals(type)) {
            return primitive.isBoolean();
        }
        
        return true;
    }
    
    /**
     * 验证字符串
     */
    private static boolean validateString(String str, JsonObject schema) {
        // 验证最小长度
        JsonElement minLengthElement = schema.get("minLength");
        if (minLengthElement != null && minLengthElement.isJsonPrimitive()) {
            int minLength = minLengthElement.getAsInt();
            if (str.length() < minLength) {
                return false;
            }
        }
        
        // 验证最大长度
        JsonElement maxLengthElement = schema.get("maxLength");
        if (maxLengthElement != null && maxLengthElement.isJsonPrimitive()) {
            int maxLength = maxLengthElement.getAsInt();
            if (str.length() > maxLength) {
                return false;
            }
        }
        
        // 验证模式
        JsonElement patternElement = schema.get("pattern");
        if (patternElement != null && patternElement.isJsonPrimitive()) {
            String pattern = patternElement.getAsString();
            try {
                if (!str.matches(pattern)) {
                    return false;
                }
            } catch (Exception e) {
                System.err.println("正则表达式错误: " + pattern);
                return false;
            }
        }
        
        // 验证枚举值
        JsonElement enumElement = schema.get("enum");
        if (enumElement != null && enumElement.isJsonArray()) {
            JsonArray enumArray = enumElement.getAsJsonArray();
            boolean found = false;
            for (JsonElement enumValue : enumArray) {
                if (enumValue.isJsonPrimitive() && enumValue.getAsString().equals(str)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 验证数字
     */
    private static boolean validateNumber(Number num, JsonObject schema, boolean isInteger) {
        double value = num.doubleValue();
        
        // 如果要求是整数，检查是否为整数
        if (isInteger && value != Math.floor(value)) {
            return false;
        }
        
        // 验证最小值
        JsonElement minimumElement = schema.get("minimum");
        if (minimumElement != null && minimumElement.isJsonPrimitive()) {
            double minimum = minimumElement.getAsDouble();
            if (value < minimum) {
                return false;
            }
        }
        
        // 验证最大值
        JsonElement maximumElement = schema.get("maximum");
        if (maximumElement != null && maximumElement.isJsonPrimitive()) {
            double maximum = maximumElement.getAsDouble();
            if (value > maximum) {
                return false;
            }
        }
        
        // 验证独占最小值
        JsonElement exclusiveMinimumElement = schema.get("exclusiveMinimum");
        if (exclusiveMinimumElement != null && exclusiveMinimumElement.isJsonPrimitive()) {
            double exclusiveMinimum = exclusiveMinimumElement.getAsDouble();
            if (value <= exclusiveMinimum) {
                return false;
            }
        }
        
        // 验证独占最大值
        JsonElement exclusiveMaximumElement = schema.get("exclusiveMaximum");
        if (exclusiveMaximumElement != null && exclusiveMaximumElement.isJsonPrimitive()) {
            double exclusiveMaximum = exclusiveMaximumElement.getAsDouble();
            if (value >= exclusiveMaximum) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 验证类型匹配
     */
    private static boolean validateType(JsonElement value, String expectedType) {
        switch (expectedType) {
            case "object":
                return value.isJsonObject();
            case "array":
                return value.isJsonArray();
            case "string":
                return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "number":
                return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "integer":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    return false;
                }
                double num = value.getAsJsonPrimitive().getAsDouble();
                return num == Math.floor(num);
            case "boolean":
                return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "null":
                return value.isJsonNull();
            default:
                return true; // 未知类型，默认通过
        }
    }
    
    /**
     * 从schema中获取类型
     */
    private static String getTypeFromSchema(JsonObject schema) {
        JsonElement typeElement = schema.get("type");
        if (typeElement != null && typeElement.isJsonPrimitive()) {
            return typeElement.getAsString();
        }
        return null;
    }
    
    /**
     * 验证并转换参数
     * @param arguments 原始参数
     * @param schema Schema定义
     * @return 转换后的参数，如果验证失败则返回null
     */
    public static JsonObject validateAndConvertParameters(JsonObject arguments, JsonObject schema) {
        if (!validateParameters(arguments, schema)) {
            return null;
        }
        
        // 这里可以添加类型转换逻辑，比如字符串转数字等
        // 目前直接返回原参数
        return arguments;
    }
}