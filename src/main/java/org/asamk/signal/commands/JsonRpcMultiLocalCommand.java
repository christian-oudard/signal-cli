package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;

import java.util.List;
import java.util.Map;

public interface JsonRpcMultiLocalCommand extends JsonRpcMultiCommand<Map<String, Object>>, MultiLocalCommand {

    default TypeReference<Map<String, Object>> getRequestType() {
        return new TypeReference<>() {};
    }

    default void handleCommand(
            Map<String, Object> request,
            MultiAccountManager c,
            JsonWriter jsonWriter
    ) throws CommandException {
        Namespace commandNamespace = new JsonRpcNamespace(request == null ? Map.of() : request);
        handleCommand(commandNamespace, c, jsonWriter);
    }

    default List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }
}
