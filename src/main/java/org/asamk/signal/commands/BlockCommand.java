package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(BlockCommand.class);

    @Override
    public String getName() {
        return "block";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Block the given contacts or groups (no messages will be received)");
        subparser.addArgument("recipient").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var contacts = ns.<String>getList("recipient");
        for (var contact : CommandUtil.getSingleRecipientIdentifiers(contacts, m.getUsername())) {
            try {
                m.setContactBlocked(contact, true);
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            }
        }

        final var groupIdStrings = ns.<String>getList("group-id");
        if (groupIdStrings != null) {
            for (var groupId : CommandUtil.getGroupIds(groupIdStrings)) {
                try {
                    m.setGroupBlocked(groupId, true);
                } catch (GroupNotFoundException e) {
                    logger.warn("Group not found {}: {}", groupId.toBase64(), e.getMessage());
                }
            }
        }
    }
}
