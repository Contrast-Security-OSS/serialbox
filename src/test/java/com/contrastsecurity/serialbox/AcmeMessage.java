package com.contrastsecurity.serialbox;

import java.util.List;

public class AcmeMessage {

	private String subject;
	private String body;
	private List<AcmeRecipient> recipients;
	
	public List<AcmeRecipient> getRecipients() {
		return recipients;
	}
	public void setRecipients(List<AcmeRecipient> recipients) {
		this.recipients = recipients;
	}
	
	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	
	public String getSubject() {
		return subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}
	
}
