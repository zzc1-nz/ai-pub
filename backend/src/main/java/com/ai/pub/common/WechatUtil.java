package com.ai.pub.common;

import com.ai.pub.config.WechatConfig;
import com.ai.pub.entity.WechatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Component
public class WechatUtil {

    private static final Logger log = LoggerFactory.getLogger(WechatUtil.class);

    private final WechatConfig wechatConfig;

    public WechatUtil(WechatConfig wechatConfig) {
        this.wechatConfig = wechatConfig;
    }

    public boolean checkSignature(String token, String signature, String timestamp, String nonce) {
        String[] arr = new String[]{token, timestamp, nonce};
        Arrays.sort(arr);
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            sb.append(s);
        }
        String temp = sha1(sb.toString());
        log.info("签名验证: token={}, timestamp={}, nonce={}, expected={}, actual={}", 
                token, timestamp, nonce, signature, temp);
        return temp != null && temp.equals(signature);
    }

    public WechatMessage parseMessage(Long accountId, String xmlData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlData.getBytes("UTF-8")));
            
            Element root = document.getDocumentElement();
            
            String toUserName = getElementText(root, "ToUserName");
            String fromUserName = getElementText(root, "FromUserName");
            String msgType = getElementText(root, "MsgType");
            String content = getElementText(root, "Content");
            String msgId = getElementText(root, "MsgId");
            
            WechatMessage message = new WechatMessage();
            message.setAccountId(accountId);
            message.setToUserName(toUserName);
            message.setOpenid(fromUserName);
            message.setMsgType(msgType);
            message.setContent(content);
            message.setMsgId(msgId);
            
            return message;
        } catch (Exception e) {
            log.error("解析微信消息XML失败", e);
            return null;
        }
    }

    public String buildReplyXml(String fromUserName, String toUserName, String content) {
        StringBuilder xml = new StringBuilder();
        xml.append("<xml>");
        xml.append("<ToUserName><![CDATA[").append(toUserName).append("]]></ToUserName>");
        xml.append("<FromUserName><![CDATA[").append(fromUserName).append("]]></FromUserName>");
        xml.append("<CreateTime>").append(System.currentTimeMillis() / 1000).append("</CreateTime>");
        xml.append("<MsgType><![CDATA[text]]></MsgType>");
        xml.append("<Content><![CDATA[").append(content).append("]]></Content>");
        xml.append("</xml>");
        return xml.toString();
    }

    private String getElementText(Element root, String tagName) {
        NodeList nodeList = root.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    private String sha1(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(str.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String shaHex = Integer.toHexString(b & 0xFF);
                if (shaHex.length() < 2) {
                    hexString.append(0);
                }
                hexString.append(shaHex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA1加密失败", e);
            return null;
        }
    }
}