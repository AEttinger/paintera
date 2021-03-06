package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.scijava.plugin.Plugin;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class CompositeSerializer implements PainteraSerialization.PainteraSerializer<Composite<?, ?>>, JsonDeserializer<Composite<?, ?>>
{

	@Override
	public Composite<?, ?> deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		try
		{
			return (Composite<?, ?>) Class.forName(json.getAsString()).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public JsonElement serialize(final Composite<?, ?> src, final Type typeOfSrc, final JsonSerializationContext
			context)
	{
		return new JsonObject();
	}

	@Override
	public Class<Composite<?, ?>> getTargetClass() {
		return (Class<Composite<?, ?>>) (Class<?>) Composite.class;
	}

	@Override
	public boolean isHierarchyAdapter() {
		return true;
	}
}
