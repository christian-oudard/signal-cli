package org.asamk.signal.manager;

import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface Manager extends Closeable {

    static Manager init(
            String username,
            File settingsPath,
            ServiceEnvironment serviceEnvironment,
            String userAgent,
            TrustNewIdentity trustNewIdentity
    ) throws IOException, NotRegisteredException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            throw new NotRegisteredException();
        }

        var account = SignalAccount.load(pathConfig.getDataPath(), username, true, trustNewIdentity);

        if (!account.isRegistered()) {
            throw new NotRegisteredException();
        }

        final var serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);

        return new ManagerImpl(account, pathConfig, serviceEnvironmentConfig, userAgent);
    }

    static List<String> getAllLocalUsernames(File settingsPath) {
        var pathConfig = PathConfig.createDefault(settingsPath);
        final var dataPath = pathConfig.getDataPath();
        final var files = dataPath.listFiles();

        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .collect(Collectors.toList());
    }

    String getUsername();

    RecipientId getSelfRecipientId();

    int getDeviceId();

    void checkAccountState() throws IOException;

    Map<String, Pair<String, UUID>> areUsersRegistered(Set<String> numbers) throws IOException;

    void updateAccountAttributes(String deviceName) throws IOException;

    void setProfile(
            String givenName, String familyName, String about, String aboutEmoji, Optional<File> avatar
    ) throws IOException;

    void unregister() throws IOException;

    void deleteAccount() throws IOException;

    void submitRateLimitRecaptchaChallenge(String challenge, String captcha) throws IOException;

    List<Device> getLinkedDevices() throws IOException;

    void removeLinkedDevices(int deviceId) throws IOException;

    void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException;

    void setRegistrationLockPin(Optional<String> pin) throws IOException, UnauthenticatedResponseException;

    Profile getRecipientProfile(RecipientId recipientId);

    List<GroupInfo> getGroups();

    SendGroupMessageResults quitGroup(
            GroupId groupId, Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException;

    void deleteGroup(GroupId groupId) throws IOException;

    Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, File avatarFile
    ) throws IOException, AttachmentInvalidException;

    SendGroupMessageResults updateGroup(
            GroupId groupId,
            String name,
            String description,
            Set<RecipientIdentifier.Single> members,
            Set<RecipientIdentifier.Single> removeMembers,
            Set<RecipientIdentifier.Single> admins,
            Set<RecipientIdentifier.Single> removeAdmins,
            boolean resetGroupLink,
            GroupLinkState groupLinkState,
            GroupPermission addMemberPermission,
            GroupPermission editDetailsPermission,
            File avatarFile,
            Integer expirationTimer,
            Boolean isAnnouncementGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException;

    Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException;

    void sendTypingMessage(
            TypingAction action, Set<RecipientIdentifier> recipients
    ) throws IOException, UntrustedIdentityException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    void sendReadReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException, UntrustedIdentityException;

    void sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException, UntrustedIdentityException;

    SendMessageResults sendMessage(
            Message message, Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendRemoteDeleteMessage(
            long targetSentTimestamp, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendMessageReaction(
            String emoji,
            boolean remove,
            RecipientIdentifier.Single targetAuthor,
            long targetSentTimestamp,
            Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) throws IOException;

    void setContactName(
            RecipientIdentifier.Single recipient, String name
    ) throws NotMasterDeviceException, UnregisteredUserException;

    void setContactBlocked(
            RecipientIdentifier.Single recipient, boolean blocked
    ) throws NotMasterDeviceException, IOException;

    void setGroupBlocked(
            GroupId groupId, boolean blocked
    ) throws GroupNotFoundException, IOException;

    void setExpirationTimer(
            RecipientIdentifier.Single recipient, int messageExpirationTimer
    ) throws IOException;

    URI uploadStickerPack(File path) throws IOException, StickerPackInvalidException;

    void requestAllSyncData() throws IOException;

    void receiveMessages(
            long timeout,
            TimeUnit unit,
            boolean returnOnTimeout,
            boolean ignoreAttachments,
            ReceiveMessageHandler handler
    ) throws IOException;

    boolean hasCaughtUpWithOldMessages();

    boolean isContactBlocked(RecipientIdentifier.Single recipient);

    File getAttachmentFile(SignalServiceAttachmentRemoteId attachmentId);

    void sendContacts() throws IOException;

    List<Pair<RecipientId, Contact>> getContacts();

    String getContactOrProfileName(RecipientIdentifier.Single recipientIdentifier);

    GroupInfo getGroup(GroupId groupId);

    List<IdentityInfo> getIdentities();

    List<IdentityInfo> getIdentities(RecipientIdentifier.Single recipient);

    boolean trustIdentityVerified(RecipientIdentifier.Single recipient, byte[] fingerprint);

    boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, String safetyNumber);

    boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, byte[] safetyNumber);

    boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient);

    String computeSafetyNumber(SignalServiceAddress theirAddress, IdentityKey theirIdentityKey);

    byte[] computeSafetyNumberForScanning(SignalServiceAddress theirAddress, IdentityKey theirIdentityKey);

    SignalServiceAddress resolveSignalServiceAddress(SignalServiceAddress address);

    SignalServiceAddress resolveSignalServiceAddress(UUID uuid);

    SignalServiceAddress resolveSignalServiceAddress(RecipientId recipientId);

    @Override
    void close() throws IOException;

    interface ReceiveMessageHandler {

        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }
}
