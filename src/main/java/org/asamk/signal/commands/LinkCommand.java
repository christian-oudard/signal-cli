package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class LinkCommand implements ProvisioningCommand {

    private static final Logger logger = LoggerFactory.getLogger(LinkCommand.class);

    @Override
    public String getName() {
        return "link";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Link to an existing device, instead of registering a new number.");
        subparser.addArgument("-n", "--name").help("Specify a name to describe this new device.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final ProvisioningManager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var writer = (PlainTextWriter) outputWriter;

        var deviceName = ns.getString("name");
        if (deviceName == null) {
            deviceName = "cli";
        }
        try {
            writer.println("{}", m.getDeviceLinkUri());
            var number = m.finishDeviceLink(deviceName);
            writer.println("Associated with: {}", number);
        } catch (TimeoutException e) {
            throw new UserErrorException("Link request timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: " + e.getMessage(), e);
        } catch (UserAlreadyExistsException e) {
            throw new UserErrorException("The user "
                    + e.getNumber()
                    + " already exists\nDelete \""
                    + e.getFileName()
                    + "\" before trying again.");
        }
    }
}
