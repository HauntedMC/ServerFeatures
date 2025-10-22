package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import java.util.List;

public class ChatFormat {
    private String identifier;
    private int index;
    private String prefix;
    private String name;
    private String suffix;
    private List<String> prefixTooltip;
    private List<String> nameTooltip;
    private List<String> suffixTooltip;
    private String preClickCmd;
    private String nameClickCmd;
    private String suffixClickCmd;

    public ChatFormat(String identifier, int index) {
        setIndex(index);
        setIdentifier(identifier);
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public List<String> getNameTooltip() {
        return this.nameTooltip;
    }

    public void setNameTooltip(List<String> tooltip) {
        this.nameTooltip = tooltip;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getPrefixTooltip() {
        return this.prefixTooltip;
    }

    public void setPrefixTooltip(List<String> preTooltip) {
        this.prefixTooltip = preTooltip;
    }

    public List<String> getSuffixTooltip() {
        return this.suffixTooltip;
    }

    public void setSuffixTooltip(List<String> suffixTooltip) {
        this.suffixTooltip = suffixTooltip;
    }

    public String getPreClickCmd() {
        return this.preClickCmd;
    }

    public void setPreClickCmd(String preClickCmd) {
        this.preClickCmd = preClickCmd;
    }

    public String getNameClickCmd() {
        return this.nameClickCmd;
    }

    public void setNameClickCmd(String nameClickCmd) {
        this.nameClickCmd = nameClickCmd;
    }

    public String getSuffixClickCmd() {
        return this.suffixClickCmd;
    }

    public void setSuffixClickCmd(String suffixClickCmd) {
        this.suffixClickCmd = suffixClickCmd;
    }
}
