package t1;


public class WindowEntry {
	
	private TftpPacket pkt;
	private boolean ack;
	int send_counter;
	long timeLimtit;
	long sendTime;
	
	
	public WindowEntry (TftpPacket pack) {
		pkt = pack;
		ack = false;
		send_counter =  0;
		sendTime = 0;
		timeLimtit = System.currentTimeMillis() + FTUdpClientSR.Timeout;
	}
	
	public void setSendTime (long sendTime) {
		this.sendTime = System.currentTimeMillis();
	}
	
	public long getSendTime () {
		return sendTime;
	}


	public TftpPacket getPkt() {
		return pkt;
	}


	public void setPkt(TftpPacket pkt) {
		this.pkt = pkt;
	}


	public boolean isAck() {
		return ack;
	}


	public void setAck(boolean ack) {
		this.ack = ack;
	}


	public int getSend_counter() {
		return send_counter;
	}
	
	public void send_counterIncrement (){
		send_counter++;
	}

	public void setRetry_counter(int send_counter) {
		this.send_counter = send_counter;
	}

	public void setTimeLimit (){
		timeLimtit = System.currentTimeMillis() + FTUdpClientSR.Timeout;
	}

	public long getTimeLimtit() {
		return timeLimtit;
	}	
}
