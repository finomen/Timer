package ru.kt15.filchenko.timer;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

class Clock {
	enum State {	
		NEW(0), RUNNING(1), PAUSED(2), STOPPED(3);
		
		int id;
		State(int id) {
			this.id = id;
		}
		
		int getId() {
			return id;
		}
		
		static State byId(int id) throws TimerCreateException {
			switch (id) {
			case 0: return NEW;
			case 1: return RUNNING;
			case 2: return PAUSED;
			case 3: return STOPPED;
			}
			
			throw new TimerCreateException("Bad state");
		}
	};
	
	private long time = 0;
	private long correction = 0;
	private State state = State.NEW;
	private final UUID id;
	private Thread worker;
	private Set<Runnable> listeners = new HashSet<Runnable>();
	
	private void load() throws TimerCreateException {
		Path state = Paths.get(".timer-" + id.toString());
		Path backupState = Paths.get(".~timer-" + id.toString());
		
		if (!state.toFile().exists() && backupState.toFile().exists()) {
			Logger.getLogger("Clock").info("Recovering state of timer " + id.toString());
			backupState.toFile().renameTo(state.toFile());
		}
		
		if (state.toFile().exists()) {
			Logger.getLogger("Clock").info("Reading state of timer " + id.toString());
			try {
				FileChannel fc = FileChannel.open(state, StandardOpenOption.READ);
				ByteBuffer buf = ByteBuffer.allocate(24);
				fc.read(buf);
				fc.close();
				
				buf.flip();
				
				long lastCorrection = buf.getLong();
				long lastSave = buf.getLong();
				int lastState = buf.getInt();
				int CRC = buf.getInt();
				
				buf.rewind();
				int realCRC = 0xFFAABBCC;
				
				while (buf.remaining() > 4) {
					realCRC = realCRC ^ buf.getInt();
				}
				
				if (realCRC != CRC) {
					Logger.getLogger("Clock").warning("Unable to save timer " + id.toString() + ": bad CRC");
					state.toFile().delete();
					throw new TimerCreateException("CRC not match, bad file");
				}
				
				correction = lastCorrection;
				this.state = State.byId(lastState);
				if (this.state == State.RUNNING) {
					this.time = System.nanoTime();
					correction += new Date().getTime() * 1000 * 1000 - lastSave;
				}
				
			} catch (IOException e) {
				Logger.getLogger("Clock").warning("Unable to load timer " + id.toString() + ": " + e.getLocalizedMessage());
				state.toFile().delete();
				throw new TimerCreateException(e);
			} catch (BufferUnderflowException e) {
				Logger.getLogger("Clock").warning("Unable to load timer " + id.toString() + ": " + e.getLocalizedMessage());
				state.toFile().delete();
				throw new TimerCreateException(e);
			}
			
		} else {
			Logger.getLogger("Clock").info("New timer timer " + id.toString());
			time = System.nanoTime();
			save();
		}
	}
	
	private void save() {
		Path state = Paths.get(".timer-" + id.toString());
		Path backupState = Paths.get(".~timer-" + id.toString());
		Path currentState = Paths.get(".!timer-" + id.toString());
		try {
			FileChannel fc = FileChannel.open(currentState, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
			ByteBuffer buf = ByteBuffer.allocate(20);
			synchronized (id) {
				buf.putLong(correction);
				buf.putLong(new Date().getTime() * 1000 * 1000);
				buf.putInt(this.state.getId());
			}
			buf.flip();
			fc.write(buf);
			buf.rewind();
			int CRC = 0xFFAABBCC;
			
			while (buf.remaining() > 0) {
				CRC = CRC ^ buf.getInt();
			}
			
			buf = ByteBuffer.allocate(4);
			buf.putInt(CRC);
			buf.flip();
			fc.write(buf);
			fc.close();
			
			backupState.toFile().delete();
			state.toFile().renameTo(backupState.toFile());
			currentState.toFile().renameTo(state.toFile());
			backupState.toFile().delete();
		} catch (IOException e) {
			Logger.getLogger("Clock").warning("Unable to save timer " + id.toString() + ": " + e.getLocalizedMessage());
		}
		
	}	
	
	Clock(UUID clockId) throws TimerCreateException {
		this.id = clockId;
		load();
		worker = new Thread(new Runnable() {
			
			@Override
			public void run() {
				while (!Thread.interrupted()) {
					synchronized (id) {
						correction = getTime();
						time = System.nanoTime();
						save();
						
						for(Runnable listener : listeners) {
							listener.run();
						}
						
						try {
							id.wait(50);
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		});
		worker.start();
	}
	
	void start() {
		switch (state) {
		case NEW:
		case STOPPED:
			synchronized (id) {
				correction = 0;
				time = System.nanoTime();
				state = State.RUNNING;
			}
			break;
		default:
			throw new InvalidOperationError();
		}
		save();
	}
	
	void stop() {
		switch (state) {
		case RUNNING:
			synchronized (id) {
				state = State.STOPPED;
			}
			break;
		default:
			throw new InvalidOperationError();
		}
		save();
	}
	
	void pause() {
		switch (state) {
		case RUNNING:
			synchronized (id) {
				correction = getTime();
				state = State.PAUSED;
			}
			break;
		default:
			throw new InvalidOperationError();
		}
		save();
	}
	
	void resume() {
		switch (state) {
		case PAUSED:
			synchronized (id) {
				time = System.nanoTime();
				state = State.RUNNING;
			}
			break;
		default:
			throw new InvalidOperationError();
		}
		save();
	}
	
	State getState() {
		synchronized (id) {
			return state;
		}
	}
	
	long getTime() {
		synchronized (id) {
			long ctime = System.nanoTime();
			if (state == State.RUNNING) {
				return ctime - time + correction;
			} else {
				return correction;
			}
		}
	}
	
	void destroy() throws InterruptedException {
		worker.interrupt();
		worker.join();
		Path state = Paths.get(".timer-" + id.toString());
		Path backupState = Paths.get(".~timer-" + id.toString());
		Path currentState = Paths.get(".!timer-" + id.toString());
		state.toFile().delete();
		backupState.toFile().delete();
		currentState.toFile().delete();
	}
	
	UUID getId() {
		return id;
	}
	
	void addListener(Runnable r) {
		synchronized(id) {
			listeners.add(r);
		}
	}
}
