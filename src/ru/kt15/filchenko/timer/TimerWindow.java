package ru.kt15.filchenko.timer;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.UUID;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import ru.kt15.filchenko.timer.Clock.State;

import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class TimerWindow {

	private JFrame frmTimer;
	private Clock c;
	private final JLabel label = new JLabel("Time:");
	private JLabel timerStatus;
	private JLabel timerValue;
	private JButton pauseResume;
	private JButton stop;
	private JButton start;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		final UUID id;
		if (args.length == 1) {
			id = UUID.fromString(args[0]);
		} else {
			id = UUID.randomUUID();
		}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TimerWindow window = new TimerWindow(id);
					window.frmTimer.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public TimerWindow(UUID id) {
		try {
			c = new Clock(id);
		} catch (TimerCreateException e) {
			e.printStackTrace();
		}
		initialize();
		c.addListener(new Runnable() {

			@Override
			public void run() {
				EventQueue.invokeLater(new Runnable() {

					@Override
					public void run() {
						State st = c.getState();
						long time = c.getTime();
						timerStatus.setText(st.toString());
						switch(st) {
						case NEW:
							start.setEnabled(true);
							stop.setEnabled(false);
							pauseResume.setEnabled(false);
							pauseResume.setText("Pause");
							break;
						case RUNNING:
							start.setEnabled(false);
							stop.setEnabled(true);
							pauseResume.setEnabled(true);
							pauseResume.setText("Pause");
							break;
						case PAUSED:
							start.setEnabled(false);
							stop.setEnabled(false);
							pauseResume.setEnabled(true);
							pauseResume.setText("Resume");
							break;
						case STOPPED:
							start.setEnabled(true);
							stop.setEnabled(false);
							pauseResume.setEnabled(false);
							pauseResume.setText("Pause");
							break;
						}
						
						time /= 10000000;
						long dec = time % 100;
						time /= 100;
						long seconds = time % 60;
						time /= 60;
						long minutes = time % 60;
						time /= 60;
						long hours = time;
						
						String timeStr = String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, dec);
						timerValue.setText(timeStr);
					}

				});
			}
		});
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmTimer = new JFrame();
		frmTimer.setTitle(c.getId().toString());
		frmTimer.setBounds(100, 100, 301, 113);
		frmTimer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel panel = new JPanel();
		frmTimer.getContentPane().add(panel, BorderLayout.WEST);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		start = new JButton("Start");
		start.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				c.start();
			}
		});
		start.setMaximumSize(new Dimension(500, 500));
		panel.add(start);

		stop = new JButton("Stop");
		stop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				c.stop();
			}
		});
		stop.setMaximumSize(new Dimension(500, 500));
		panel.add(stop);

		pauseResume = new JButton("Pause");
		pauseResume.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (c.getState() == Clock.State.PAUSED) {
					c.resume();
				} else {
					c.pause();
				}
			}
		});
		pauseResume.setMaximumSize(new Dimension(500, 500));
		panel.add(pauseResume);

		JPanel panel_1 = new JPanel();
		frmTimer.getContentPane().add(panel_1, BorderLayout.CENTER);
		panel_1.setLayout(new GridLayout(2, 2, 0, 0));

		JLabel lblStatus = new JLabel("Status:");
		panel_1.add(lblStatus);

		timerStatus = new JLabel("NEW");
		panel_1.add(timerStatus);
		panel_1.add(label);

		timerValue = new JLabel("00:00:00.00");
		panel_1.add(timerValue);
	}

}
