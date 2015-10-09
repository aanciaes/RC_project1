package t1;

public class WindowEntry {
	
	private TftpPacket pkt;
	private boolean ack;
	int retry_counter;
	long timeLimtit;
	
	
	public WindowEntry (TftpPacket pack) {
		pkt = pack;
		ack = false;
		retry_counter =  0;
		timeLimtit = System.currentTimeMillis() + FTUdpClient.DEFAULT_TIMEOUT;
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


	public int getRetry_counter() {
		return retry_counter;
	}


	public void setRetry_counter(int retry_counter) {
		this.retry_counter = retry_counter;
	}


	public long getTimeLimtit() {
		return timeLimtit;
	}	
}
