package it.smartcommunitylabdhub.framework.k8s.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import java.io.IOException;
import java.util.Map;

public class QuantityJacksonDeserializer extends StdDeserializer<Quantity> {


    public QuantityJacksonDeserializer() {
        this(null);
    }

    public QuantityJacksonDeserializer(Class<Quantity> t) {
        super(IntOrString.class);
    }

    @Override
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        Object value = p.readValueAs(Object.class);  // Read the value as an Object (can be String or Map)

        if (value instanceof String) {
            return new Quantity((String) value);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object number = map.get("number");
            Object format = map.get("format");

            if (number instanceof Number) {
                double num = ((Number) number).doubleValue();
                if ("DECIMAL_SI".equals(format)) {
                    return new Quantity((int) (num * 1000) + "m");
                }
                return new Quantity(String.valueOf(num));
            }
        }
        throw new IOException("Invalid Quantity format: " + value);
    }
}

