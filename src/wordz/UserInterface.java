package wordz;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sgates
 * Date: 3/11/12
 * Time: 12:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class UserInterface {
    private JRadioButton generatedAlphabetOnlyRadioButton;
    private JRadioButton generatedAndCustomRadioButton;
    private JRadioButton customAlphabetOnlyRadioButton;
    private JTextField customAlphabetField;
    private JTextField customWordlistField;
    private JButton generateButton;
    private JProgressBar generateProgress;
    private JCheckBox uppercaseCheckBox;
    private JCheckBox numbersCheckBox;
    private JCheckBox spacesCheckBox;
    private JCheckBox lowercaseCheckBox;
    private JCheckBox symbolsCheckBox;
    private JCheckBox wordlistCheckbox;
    private JButton chooseWordlistButton;
    private JTextField minField;
    private JTextField maxField;
    private JLabel totalWordsLabel;
    private JLabel fileSizeLabel;
    private JLabel wordsPerSecLabel;
    private JPanel UIPanel;
    private JButton calculateButton;
    private JButton stopButton;
    private JButton clearButton;
    private JLabel timeRemainingLabel;

    private File userWordlistFile;
    private ArrayList<String> userWordlist;
    private ArrayList<String> defaultWordlist;
    private ArrayList<String> namelist;
    private File outFile = new File("XXX_WordPounder.txt");
    private String[] alphabet;
    private long totalWords = 0;
    private Thread wordlistWorker;
    private Thread permutationWorker;
    private DecimalFormat df = new DecimalFormat("#.##");
    private DecimalFormat smallPercent = new DecimalFormat("#.#####");
    private long generateStartTime;
    private long sizeInBytes;

    public UserInterface() {
        generateProgress.setMaximum(1000);
        chooseWordlistButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return !file.isDirectory();
                    }

                    @Override
                    public String getDescription() {
                        return "Accepts only files, not folders!";
                    }
                });
                if (chooser.showOpenDialog(UIPanel) == JFileChooser.APPROVE_OPTION) {
                    userWordlistFile = chooser.getSelectedFile();
                }

                if (userWordlistFile != null) {
                    customWordlistField.setText(userWordlistFile.getAbsolutePath());
                }
            }
        });

        generateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                prepareToGenerate();

                if (sizeInBytes >= 536870912000L) {
                    String size = df.format(sizeInBytes / 1073741824D) + "GB";
                    int response = JOptionPane.showConfirmDialog(UIPanel, "You are about to generate a wordlist\nthat will be approximately " + size + "." +
                        "\n\nAre you sure you want to do this?", "Massive Wordlist Warning!", JOptionPane.WARNING_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }

                //Make a new printWriter to write the wordlist out
                PrintWriter out = null;
                try {
                    out = new PrintWriter(outFile);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }

                lastTime = System.currentTimeMillis();
                wpsCount = 0;
                generateStartTime = System.currentTimeMillis();

                ArrayList<String> wordlistToUse = null;
                if (wordlistCheckbox.isSelected()) {
                    if (userWordlist == null) {
                        wordlistToUse = defaultWordlist;
                    } else {
                        wordlistToUse = userWordlist;
                    }
                    totalWords += wordlistToUse.size();

                    //Write words to the file
                    writeArray(wordlistToUse, out);
                }

                //Write the alphabet permutations to a file
                writeWordlistFromAlphabet(out, wordlistToUse != null ? wordlistToUse.size() : 0);
                long timeTaken = System.currentTimeMillis() - generateStartTime;
                if (timeTaken > 0) {
                    wordsPerSecLabel.setText("Words/Sec: " + (totalWords / timeTaken / 1000));
                } else {
                    wordsPerSecLabel.setText("Words/Sec: >" + totalWords);
                }
            }
        });
        calculateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                prepareToGenerate();
                wordsPerSecLabel.setText("Words/Sec: 0");
            }
        });
        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (wordlistWorker != null) {
                    wordlistWorker.interrupt();
                }
                if (permutationWorker != null) {
                    permutationWorker.interrupt();
                }
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                userWordlistFile = null;
                userWordlist = null;
                namelist = null;
                alphabet = null;
                totalWords = 0;
                if (wordlistWorker != null) {
                    wordlistWorker.interrupt();
                }
                wordlistWorker = null;
                if (permutationWorker != null) {
                    permutationWorker.interrupt();
                }
                permutationWorker = null;
                generateStartTime = 0;
                sizeInBytes = 0;
                generatedAlphabetOnlyRadioButton.setSelected(true);
                customAlphabetField.setText(null);
                customWordlistField.setText(null);
                generateProgress.setString(null);
                generateProgress.setValue(0);
                uppercaseCheckBox.setSelected(false);
                numbersCheckBox.setSelected(false);
                spacesCheckBox.setSelected(false);
                lowercaseCheckBox.setSelected(true);
                symbolsCheckBox.setSelected(false);
                wordlistCheckbox.setSelected(false);
                minField.setText("1");
                maxField.setText("6");
                totalWordsLabel.setText("Total Words: 0");
                fileSizeLabel.setText("Total File Size: 0");
                wordsPerSecLabel.setText("Words/Sec: 0");
                timeRemainingLabel.setText("Time Remaining:");

                wpsCount = 0;
                lastTime = 0;
                timeCount = 0;
                timeAvg = 0;
            }
        });
    }

    private long lineCount(File file) {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        try {
            byte[] c = new byte[1024];
            long count = 0;
            int readChars = 0;
            while ((readChars = is.read(c)) != -1) {
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n')
                        ++count;
                }
            }
            return count;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return -1;
    }

    private void prepareToGenerate() {
        //Compare the selected file to the wordlist field incase the user entered something custom
        if (!customWordlistField.getText().isEmpty() &&
            userWordlistFile != null &&
            !customWordlistField.getText().equals(userWordlistFile.getAbsolutePath())) {
            userWordlistFile = new File(customWordlistField.getText());
            //Don't Allow Directories
            if (userWordlistFile.isDirectory()) {
                userWordlistFile = null;
                JOptionPane.showMessageDialog(UIPanel, "You can only select files as " +
                    "wordlists, not directories!");
                return;
            }
        }

        if (defaultWordlist == null) {
            defaultWordlist = loadDefaultWordlist();
        }

        if (userWordlistFile != null) {
            userWordlist = loadUserWordlist();
        }

        totalWords = userWordlistFile != null ? userWordlist.size() : defaultWordlist.size();

        List<String> tmp = new ArrayList<String>();
        //Pick an alphabet
        switch (getAlphaSelection()) {
            case 1:
                tmp.addAll(generateAlphabet());
                break;
            case 2:
                ArrayList<String> a = customAlphabet();
                if (a == null) {
                    JOptionPane.showMessageDialog(UIPanel, "You cannot have a blank custom alphabet field if you are trying to use an custom alphabet!");
                    return;
                }
                tmp.addAll(a);
                break;
            case 3:
                tmp.addAll(generateAlphabet());
                tmp.addAll(customAlphabet());
                break;
            default:
                JOptionPane.showMessageDialog(UIPanel, "You managed to choose an invalud alpha radio button!?! Try again");
        }

        //Generate the alphabet and save it in a simple array for speed
        alphabet = new String[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) {
            alphabet[i] = tmp.get(i);
        }

        calculatePermutationCount(
            alphabet.length,
            Integer.parseInt(minField.getText()),
            Integer.parseInt(maxField.getText())
        );
    }

    private void calculatePermutationCount(int alphabetSize, int min, int max) {
        long total = 0;
        sizeInBytes = 0;
        String formattedSize = "";
        for (long i = min; i <= max; i++) {
            long count = (long) Math.pow(alphabetSize, i);
            total += count;
            sizeInBytes += count * (i + 1);
        }
        totalWords += total;

        if (sizeInBytes < 1024L) {
            formattedSize = sizeInBytes + "B";
        } else if (sizeInBytes < 1048576L) {
            formattedSize = df.format(sizeInBytes / 1024D) + "KB";
        } else if (sizeInBytes < 1073741824L) {
            formattedSize = df.format(sizeInBytes / 1048576D) + "MB";
        } else if (sizeInBytes < 1099511627776L) {
            formattedSize = df.format(sizeInBytes / 1073741824D) + "GB";
        } else {
            formattedSize = df.format(sizeInBytes / 1099511627776D) + "TB";
        }

        totalWordsLabel.setText("Total Words: " + totalWords);
        fileSizeLabel.setText("Total File Size: " + formattedSize);
    }

    private void writeWordlistFromAlphabet(PrintWriter out, long prog) {
        if (permutationWorker != null && !permutationWorker.isInterrupted()) {
            permutationWorker.interrupt();
        }
        permutationWorker = new PermutationWriter(out, prog);
        if (wordlistWorker != null) {
            try {
                wordlistWorker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        permutationWorker.start();
    }

    private class PermutationWriter extends Thread {
        long progress;
        PrintWriter out;

        public PermutationWriter(PrintWriter out, long progress) {
            this.progress = progress;
            this.out = out;
        }

        public void run() {
            int min = Integer.parseInt(minField.getText());
            int max = Integer.parseInt(maxField.getText());
            StringBuffer sb = new StringBuffer();

            //For each word length
            int tmpWordCound = 0;
            for (int i = min; i <= max; i++) {
                int[] indexes = new int[i];
                //Init the index array
                for (int j = 0; j < indexes.length; j++) {
                    indexes[j] = 0;
                }

                //make permutations
                long maxWords = (long) Math.pow(alphabet.length, indexes.length);
                for (int wordSetCount = 0; wordSetCount < maxWords; wordSetCount++) {
                    if (isInterrupted()) {
                        return;
                    }
                    progress++;
                    //Write the buffer in 10000 word blocks
                    if (++tmpWordCound == 10000) {
                        tmpWordCound = 0;
                        writeStringBuffer(sb, out);
                        sb.setLength(0);    //Clear the buffer for next run
                        setProgressBar(progress);
                    }
                    //iterate over indexes looking at each
                    for (int j = indexes.length - 1; j >= 0; j--) {
                        sb.append(alphabet[indexes[j]]);
                    }
                    sb.append("\n");
                    updateIndexes(0, alphabet.length, indexes);
                }
                writeStringBuffer(sb, out);
                sb.setLength(0);
                setProgressBar(progress);
            }
            generateProgress.setString(null);
            generateProgress.setValue(1000);
            out.close();
        }
    }

    long wpsCount;
    private long lastTime;
    int timeCount = 0;
    long timeAvg = 0;

    private void setProgressBar(long progress) {
        double p = ((double) progress / (double) totalWords);
        int reduced = (int) (p * 1000);
        generateProgress.setValue(reduced);
        generateProgress.setString(progress + "/" + totalWords + " (" + smallPercent.format(p * 100) + "%)");
        if (System.currentTimeMillis() - lastTime >= 500) {
            wordsPerSecLabel.setText("Words/Sec: " + ((progress - wpsCount) * 2));
            wpsCount = progress;
            lastTime = System.currentTimeMillis();
        }

        long elapsedSeconds = (System.currentTimeMillis() - generateStartTime) / 1000;
        timeAvg += (long)(elapsedSeconds * (1-p)/p);

        if (++timeCount == 1000) {
            timeAvg /= 1000;
            int minutes = (int) (timeAvg / 60);
            int seconds = (int) (timeAvg % 60);

            timeRemainingLabel.setText("Time Remainging: " + minutes + "M " + seconds + "S");
            timeCount = 0;
            timeAvg = 0;
        }
    }

    private boolean updateIndexes(int depth, int alphabetSize, int[] indexes) {
        if (depth == indexes.length) {
            return false;
        }
        indexes[depth]++;
        if (indexes[depth] == alphabetSize) {
            indexes[depth] = 0;
            updateIndexes(depth + 1, alphabetSize, indexes);
        }
        return true;
    }

    private void writeStringBuffer(StringBuffer sb, PrintWriter out) {
        out.print(sb.toString());
    }

    private void writeArray(ArrayList<String> a, PrintWriter out) {
        if (wordlistWorker != null && !wordlistWorker.isInterrupted()) {
            wordlistWorker.interrupt();
        }
        wordlistWorker = new ArrayWriter(a, out);
        if (permutationWorker != null) {
            try {
                permutationWorker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        wordlistWorker.start();
    }

    private class ArrayWriter extends Thread {
        ArrayList<String> a;
        PrintWriter out;

        public ArrayWriter(ArrayList<String> a, PrintWriter out) {
            this.a = a;
            this.out = out;
        }

        public void run() {
            long start = System.currentTimeMillis();
            long inc = 0;
            for (long i = 0; i < a.size(); i++) {
                if (isInterrupted()) {
                    return;
                }
                out.println(a.get((int) i));
                if (inc++ == 100) {
                    setProgressBar(i);
                    inc = 0;
                }
            }
        }
    }

    private ArrayList<String> generateAlphabet() {
        ArrayList<String> a = new ArrayList<String>();
        if (lowercaseCheckBox.isSelected()) {
            for (int i = 97; i < 123; i++) {
                a.add("" + (char) i);
            }
        }
        if (symbolsCheckBox.isSelected()) {
            for (int i = 33; i < 48; i++) {
                a.add("" + (char) i);
            }
            for (int i = 58; i < 65; i++) {
                a.add("" + (char) i);
            }
            for (int i = 91; i < 97; i++) {
                a.add("" + (char) i);
            }
            for (int i = 123; i < 127; i++) {
                a.add("" + (char) i);
            }
        }

        if (uppercaseCheckBox.isSelected()) {
            for (int i = 65; i < 91; i++) {
                a.add("" + (char) i);
            }
        }

        if (numbersCheckBox.isSelected()) {
            for (int i = 48; i < 58; i++) {
                a.add("" + (char) i);
            }
        }

        if (spacesCheckBox.isSelected()) {
            a.add("" + (char) 32);
        }

        return a;
    }

    private ArrayList<String> loadUserWordlist() {
        ArrayList<String> a = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(userWordlistFile));
            String line = null;
            while ((line = in.readLine()) != null) {
                if (line.length() >= (Integer.parseInt(minField.getText())) &&
                    line.length() <= (Integer.parseInt(maxField.getText()))) {
                    a.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return a;
    }

    private ArrayList<String> loadDefaultWordlist() {
        ArrayList<String> a = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(new File("wordlist.txt")));
            String line = null;
            while ((line = in.readLine()) != null) {
                if (line.length() >= (Integer.parseInt(minField.getText())) &&
                    line.length() <= (Integer.parseInt(maxField.getText()))) {
                    a.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return a;
    }

    private ArrayList<String> customAlphabet() {
        if (customAlphabetField.getText().isEmpty()) {
            return null;
        }
        Set<String> a = new LinkedHashSet<String>();
        for (int i = 0; i < customAlphabetField.getText().length(); i++) {
            a.add("" + customAlphabetField.getText().charAt(i));
        }
        ArrayList<String> alpha = new ArrayList<String>();
        alpha.addAll(a);
        return alpha;
    }

    private int getAlphaSelection() {
        if (generatedAlphabetOnlyRadioButton.isSelected()) {
            return 1;
        } else if (customAlphabetOnlyRadioButton.isSelected()) {
            return 2;
        } else if (generatedAndCustomRadioButton.isSelected()) {
            return 3;
        }
        return -1;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("UserInterface");
        frame.setContentPane(new UserInterface().UIPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
