package commonmodels.transport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "header",
        "sender",
        "receiver",
        "followup",
        "attachment",
        "epoch"
})
public class Request implements Serializable
{

    @JsonProperty("header")
    private String header;
    @JsonProperty("sender")
    private String sender;
    @JsonProperty("receiver")
    private String receiver;
    @JsonProperty("followup")
    private String followup;
    @JsonProperty("attachment")
    private String attachment;
    @JsonProperty("epoch")
    private long epoch;

    private Object largeAttachment;

    private final static long serialVersionUID = 2464827715445932636L;

    /**
     * No args constructor for use in serialization
     *
     */
    public Request() {
    }

    /**
     *
     * @param sender
     * @param receiver
     * @param epoch
     * @param attachment
     * @param followup
     * @param header
     */
    public Request(String header, String sender, String receiver, String followup, String attachment, long epoch) {
        super();
        this.header = header;
        this.sender = sender;
        this.receiver = receiver;
        this.followup = followup;
        this.attachment = attachment;
        this.epoch = epoch;
    }

    @JsonProperty("header")
    public String getHeader() {
        return header;
    }

    @JsonProperty("header")
    public void setHeader(String header) {
        this.header = header;
    }

    public Request withHeader(String header) {
        this.header = header;
        return this;
    }

    @JsonProperty("sender")
    public String getSender() {
        return sender;
    }

    @JsonProperty("sender")
    public void setSender(String sender) {
        this.sender = sender;
    }

    public Request withSender(String sender) {
        this.sender = sender;
        return this;
    }

    @JsonProperty("receiver")
    public String getReceiver() {
        return receiver;
    }

    @JsonProperty("receiver")
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Request withReceiver(String receiver) {
        this.receiver = receiver;
        return this;
    }

    @JsonProperty("followup")
    public String getFollowup() {
        return followup;
    }

    @JsonProperty("followup")
    public void setFollowup(String followup) {
        this.followup = followup;
    }

    public Request withFollowup(String followup) {
        this.followup = followup;
        return this;
    }

    @JsonProperty("attachment")
    public String getAttachment() {
        return attachment;
    }

    @JsonProperty("attachment")
    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    public Request withAttachment(String attachment) {
        this.attachment = attachment;
        return this;
    }

    public Request withAttachments(Object... attachments) {
        StringBuilder result = new StringBuilder();
        for (Object str : attachments) {
            result.append(str).append(" ");
        }

        this.attachment = result.toString().trim();
        return this;
    }

    public Object getLargeAttachment() {
        return largeAttachment;
    }

    public void setLargeAttachment(Object largeAttachment) {
        this.largeAttachment = largeAttachment;
    }

    public Request withLargeAttachment(Object largeAttachment) {
        this.largeAttachment = largeAttachment;
        return this;
    }

    @JsonProperty("epoch")
    public long getEpoch() {
        return epoch;
    }

    @JsonProperty("epoch")
    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public Request withEpoch(long epoch) {
        this.epoch = epoch;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                        .append("        \n").append("header", header)
                        .append("        \n").append("sender", sender)
                        .append("        \n").append("receiver", receiver)
                        .append("        \n").append("followup", followup)
                        .append("        \n").append("attachment", attachment)
                        .append("        \n").append("epoch", epoch)
                        .append("        \n").toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(sender).append(receiver).append(epoch).append(attachment).append(followup).append(header).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Request) == false) {
            return false;
        }
        Request rhs = ((Request) other);
        return new EqualsBuilder().append(sender, rhs.sender).append(receiver, rhs.receiver).append(epoch, rhs.epoch).append(attachment, rhs.attachment).append(followup, rhs.followup).append(header, rhs.header).isEquals();
    }

    public String toCommand() {
        String command = "";

        if (header != null)
            command += header + " ";

        if (receiver != null)
            command += receiver + " ";

        if (attachment != null)
            command +=  attachment + " ";

        return command.trim();
    }
}