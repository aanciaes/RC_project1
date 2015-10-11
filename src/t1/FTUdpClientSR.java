package t1;

import static t1.TftpPacket.MAX_TFTP_PACKET_SIZE;
import static t1.TftpPacket.OP_ACK;
import static t1.TftpPacket.OP_DATA;
import static t1.TftpPacket.OP_WRQ;

import java.awt.Window;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FTUdpClientSR {

	static final int DEFAULT_TIMEOUT = 3000;
	static final int DEFAULT_MAX_RETRIES = 5;
	static final int DEFAULT_BLOCKSIZE = 512; // default block size as in TFTP
												// RFC

	static int WindowSize = 5; // window size for testing 1/30/60
	static int BlockSize = DEFAULT_BLOCKSIZE;
	static int Timeout = DEFAULT_TIMEOUT;

	private String filename;

	RandomAccessFile raf;

	private TreeMap<Long, WindowEntry> window;
	long byteCount = 1; // block byte count starts at 1

	private DatagramSocket socket;
	private BlockingQueue<TftpPacket> receiverQueue;
	volatile private SocketAddress srvAddress;

	FTUdpClientSR(String filename, SocketAddress srvAddress) throws FileNotFoundException {
		this.filename = filename;
		this.srvAddress = srvAddress;
		window = new TreeMap<Long, WindowEntry>();

		this.raf = new RandomAccessFile("debug" + ".txt", "rw");
	}

	void sendFile() {
		try {

			// socket = new DatagramSocket();
			socket = new MyDatagramSocket();

			// create producer/consumer queue for ACKs
			receiverQueue = new ArrayBlockingQueue<>(1);

			// start a receiver process to feed the queue
			new Thread(() -> {
				try {
					for (;;) {
						byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
						DatagramPacket msg = new DatagramPacket(buffer, buffer.length);
						socket.receive(msg);

						// update server address (it may change due to WRQ
						// coming from a different port
						srvAddress = msg.getSocketAddress();

						// make the packet available to sender process
						TftpPacket pkt = new TftpPacket(msg.getData(), msg.getLength());
						receiverQueue.put(pkt);
					}
				} catch (Exception e) {
				}
			}).start();

			System.out.println("sending file: \"" + filename + "\" to server: " + srvAddress + " from local port:"
					+ socket.getLocalPort());

			TftpPacket wrr = new TftpPacket().putShort(OP_WRQ).putString(filename).putByte(0).putString("octet")
					.putByte(0).putString("selective_repeat").putByte(0).putString("true").putByte(0)
					.putString("blksize").putByte(0).putString("" + BlockSize).putByte(0);

			// First packet, connecting packet
			sendInitial(wrr, 0L, DEFAULT_MAX_RETRIES);

			try {

				FileInputStream f = new FileInputStream(filename);

				int freeSlots = WindowSize;

				while (f.available() > 0) { // Checks end of file, returns 0 if
											// file is position beyond EOF
					// Reading blocks to window
					freeSlots = fillWindow(f, freeSlots);
					sendDataFromWindow();
					// boolean time = true;

					// while (!windowFull() && time) {
					TftpPacket ack = receiverQueue.poll(Timeout, TimeUnit.MILLISECONDS);
					System.err.println(">>>> got: " + ack);

					if (ack != null)
						if (ack.getOpcode() == OP_ACK)
							if (window.containsKey(ack.getBlockSeqN())) {
								window.get(ack.getBlockSeqN()).setAck(true);
								freeSlots = handleSliding(freeSlots);
							} else {
								System.out.println("OLA");
								System.err.println("wrong ack ignored, block= " + ack.getBlockSeqN());
							}
						else {
							System.err.println("error +++ (unexpected packet)");
						}
					else {
						// time = false;
						System.err.println("timeout...");
					}
				}
				// freeSlots = handleSliding(freeSlots);
				// }

				TftpPacket ack;
				while ((ack = receiverQueue.poll(Timeout, TimeUnit.MILLISECONDS)) != null) {
					// TftpPacket ack = receiverQueue.poll(Timeout,
					// TimeUnit.MILLISECONDS);
					System.err.println(">>>> got delayed: " + ack);

					if (ack.getOpcode() == OP_ACK)
						if (window.containsKey(ack.getBlockSeqN())) {
							window.get(ack.getBlockSeqN()).setAck(true);
							// freeSlots = handleSliding(freeSlots);
						} else {
							System.out.println("OLA");
							System.err.println("wrong ack ignored, block= " + ack.getBlockSeqN());
						}
					else {
						System.err.println("error +++ (unexpected packet)");
					}
				}

				// Send an empty block to signal the end of file.
				TftpPacket pkt = new TftpPacket().putShort(OP_DATA).putLong(byteCount).putBytes(new byte[0], 0);
				sendFinal(pkt, byteCount, DEFAULT_MAX_RETRIES);

				f.close();

			} catch (Exception e) {
				System.err.println("Failed with error \n" + e.getMessage());
				e.printStackTrace();
			}
			socket.close();
			System.out.println("Done...");
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	void sendInitial(TftpPacket blk, long expectedACK, int retries) throws Exception {
		sendControl(blk, expectedACK, retries);
	}

	void sendFinal(TftpPacket blk, long expectedACK, int retries) throws Exception {
		sendControl(blk, expectedACK, retries);
	}

	void sendControl(TftpPacket blk, long expectedACK, int retries) throws Exception {
		for (int i = 0; i < retries; i++) {
			System.err.println("sending: " + blk + " expecting:" + expectedACK);
			socket.send(new DatagramPacket(blk.getPacketData(), blk.getLength(), srvAddress));
			TftpPacket ack = receiverQueue.poll(Timeout, TimeUnit.MILLISECONDS);
			System.err.println(">>>> got: " + ack);
			if (ack != null)
				if (ack.getOpcode() == OP_ACK)
					if (expectedACK <= ack.getBlockSeqN()) {
						return;
					} else {
						System.err.println("wrong ack ignored, block= " + ack.getBlockSeqN());
					}
				else {
					System.err.println("error +++ (unexpected packet)");
				}
			else
				System.err.println("timeout...");
		}
		throw new IOException("Too many retries");
	}

	int fillWindow(FileInputStream f, int freeSlots) throws IOException {
		int i = 0;
		int n;
		int free = freeSlots;
		byte[] buffer = new byte[BlockSize];
		try {
			while (i < freeSlots) {
				if ((n = f.read(buffer)) > 0) {
					TftpPacket pkt = new TftpPacket().putShort(OP_DATA).putLong(byteCount).putBytes(buffer, n);
					window.put(byteCount, new WindowEntry(pkt));
					byteCount += n;
					free--;
				}
				i++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return free;
	}

	void sendDataFromWindow() throws IOException {
		Collection<WindowEntry> c = window.values();

		Iterator<WindowEntry> t = c.iterator();

		while (t.hasNext()) {
			WindowEntry w = t.next();
			if (!w.isAck()) {
				if (w.getSend_counter() == 0) {
					System.err.println("sending: " + w.getPkt() + " expecting:" + w.getPkt().getBlockSeqN());
					socket.send(new DatagramPacket(w.getPkt().getPacketData(), w.getPkt().getLength(), srvAddress));
					w.send_counterIncrement();
					raf.write(w.getPkt().getBlockData());
				} else {
					if (System.currentTimeMillis() > w.getTimeLimtit()) {
						System.err.println(
								"sending after timeout: " + w.getPkt() + " expecting:" + w.getPkt().getBlockSeqN());
						socket.send(new DatagramPacket(w.getPkt().getPacketData(), w.getPkt().getLength(), srvAddress));
						w.send_counterIncrement();
						raf.write(w.getPkt().getBlockData());
					}
				}
			}
		}
	}

	int handleSliding(int free) {

		Collection<WindowEntry> c = window.values();
		Iterator<WindowEntry> t = c.iterator();
		int freeSlots = free;

		for (int i = 0; i < WindowSize; i++) {
			if (t.hasNext()) {
				WindowEntry w = t.next();
				if (w.isAck()) {
					System.out.println("Foi removido = " + w.getPkt().getBlockSeqN());
					t.remove();
					// window.remove(w.getPkt().getBlockSeqN());
					freeSlots++;
				} else {
					return freeSlots;
				}
			}
		}

		return freeSlots;
	}

	boolean windowFull() {
		Collection<WindowEntry> c = window.values();
		Iterator<WindowEntry> t = c.iterator();
		int n = 0;

		while (t.hasNext()) {
			if (t.next().isAck())
				n++;
		}
		return n == WindowSize;
	}

	public static void main(String[] args) throws Exception {
		MyDatagramSocket.init(1, 1);

		switch (args.length) {
		case 5:
			WindowSize = Integer.parseInt(args[4]);
		case 4:
			Timeout = Integer.parseInt(args[3]);
		case 3:
			BlockSize = Integer.valueOf(args[2]);
		case 2:
			break;
		default:
			System.out.printf("usage: java FTUdpClientSR filename servidor [blocksize [ timeout [ windowsize ]]]\n");
			System.exit(0);
		}

		String filename = args[0];

		// Preparar endereco e o porto do servidor
		String server = args[1];
		SocketAddress srvAddr = new InetSocketAddress(server, FTUdpServer.DEFAULT_PORT);

		new FTUdpClientSR(filename, srvAddr).sendFile();
	}

}
