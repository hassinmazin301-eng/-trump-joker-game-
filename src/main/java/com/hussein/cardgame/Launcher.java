package com.hussein.cardgame;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Launcher {

    public static void main(String[] args) {
        Application.launch(Game.class, args);
    }

    public static class Game extends Application {

        private enum Suit {
            SPADES("♠ بستوني", "♠", false),
            HEARTS("♥ قلوب", "♥", true),
            DIAMONDS("♦ ألماس", "♦", true),
            CLUBS("♣ سباتي", "♣", false);

            final String title;
            final String symbol;
            final boolean red;

            Suit(String title, String symbol, boolean red) {
                this.title = title;
                this.symbol = symbol;
                this.red = red;
            }

            @Override
            public String toString() {
                return title;
            }
        }

        // joker: 0 = ordinary, 1 = black, 2 = red
        private record Card(Suit suit, int rank, int joker) {

            boolean isJoker() {
                return joker > 0;
            }

            boolean isRed() {
                return joker == 2 || (!isJoker() && suit.red);
            }

            String rankText() {
                return switch (rank) {
                    case 11 -> "J";
                    case 12 -> "Q";
                    case 13 -> "K";
                    case 14 -> "A";
                    default -> Integer.toString(rank);
                };
            }

            String shortText() {
                if (joker == 2) return "الجوكر الأحمر";
                if (joker == 1) return "الجوكر الأسود";
                return rankText() + suit.symbol;
            }

            String faceText() {
                if (joker == 2) return "JOKER\nأحمر";
                if (joker == 1) return "JOKER\nأسود";
                return rankText() + "\n" + suit.symbol;
            }
        }

        private record Play(int player, Card card) {}

        private record Completed(
                int number,
                List<Play> plays,
                int winner) {}

        private Stage stage;
        private Scene scene;

        private final String[] names = {
                "حسين", "أحمد", "سارة", "نور"
        };

        private final List<List<Card>> hands = new ArrayList<>();
        private final List<Play> trick = new ArrayList<>();
        private final List<Completed> history = new ArrayList<>();

        private final int[] scores = new int[4];

        private Suit trump = Suit.SPADES;
        private Suit leadSuit;

        private int dealer;
        private int currentPlayer;
        private int lastWinner;

        private boolean trickComplete;
        private boolean gameFinished;

        @Override
        public void start(Stage stage) {
            this.stage = stage;

            scene = new Scene(new StackPane(), 1180, 800);

            stage.setScene(scene);
            stage.setTitle("Trump & Joker — الحكم والجوكر");
            stage.setMinWidth(1000);
            stage.setMinHeight(720);

            showSetup();
            stage.show();
        }

        private void show(Parent root) {
            root.setStyle("""
                    -fx-font-family: 'Segoe UI';
                    -fx-font-size: 15px;
                    -fx-background-color: #eaf0f5;
                    """);

            scene.setRoot(root);
        }

        private Label title(String text) {
            Label label = new Label(text);
            label.setStyle("""
                    -fx-font-size: 27px;
                    -fx-font-weight: bold;
                    -fx-text-fill: #123b3b;
                    """);
            return label;
        }

        private Label message(String text) {
            Label label = new Label(text);
            label.setWrapText(true);
            return label;
        }

        private Button button(String text, Runnable action) {
            Button button = new Button(text);
            button.setStyle("""
                    -fx-background-color: #176b58;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-padding: 10 18;
                    -fx-background-radius: 8;
                    -fx-cursor: hand;
                    """);
            button.setOnAction(event -> action.run());
            return button;
        }

        private VBox page() {
            VBox box = new VBox(18);
            box.setPadding(new Insets(30));
            box.setAlignment(Pos.CENTER);
            return box;
        }

        private void showSetup() {
            VBox root = page();

            root.getChildren().addAll(
                    title("الحكم والجوكر"),
                    message("أربعة لاعبين — بالتناوب على نفس الجهاز")
            );

            GridPane form = new GridPane();
            form.setHgap(18);
            form.setVgap(14);
            form.setAlignment(Pos.CENTER);

            TextField[] fields = new TextField[4];

            for (int i = 0; i < 4; i++) {
                fields[i] = new TextField(names[i]);
                fields[i].setPrefWidth(230);
                form.add(new Label("اللاعب " + (i + 1)), 0, i);
                form.add(fields[i], 1, i);
            }

            ComboBox<String> dealerBox = new ComboBox<>();
            dealerBox.getItems().addAll(
                    "اللاعب الأول",
                    "اللاعب الثاني",
                    "اللاعب الثالث",
                    "اللاعب الرابع"
            );
            dealerBox.getSelectionModel().select(dealer);

            ComboBox<Suit> trumpBox = new ComboBox<>();
            trumpBox.getItems().addAll(Suit.values());
            trumpBox.setValue(trump);

            form.add(new Label("الموزّع"), 0, 4);
            form.add(dealerBox, 1, 4);

            form.add(new Label("الحكم"), 0, 5);
            form.add(trumpBox, 1, 5);

            Button start = button("ابدأ اللعبة", () -> {
                if (!saveNames(fields)) return;

                dealer = dealerBox.getSelectionModel().getSelectedIndex();
                trump = trumpBox.getValue();

                startGame();
            });

            root.getChildren().addAll(
                    form,
                    start,
                    button("قواعد اللعبة", this::showRules)
            );

            show(root);
        }

        private boolean saveNames(TextField[] fields) {
            List<String> updated = new ArrayList<>();

            for (TextField field : fields) {
                String name = field.getText().trim();

                if (name.isEmpty() || name.length() > 20) {
                    info("الأسماء",
                            "أدخل اسمًا لكل لاعب بطول من 1 إلى 20 حرفًا.");
                    return false;
                }

                boolean duplicate = updated.stream()
                        .anyMatch(existing -> existing.equalsIgnoreCase(name));

                if (duplicate) {
                    info("الأسماء", "يجب أن يكون لكل لاعب اسم مختلف.");
                    return false;
                }

                updated.add(name);
            }

            for (int i = 0; i < 4; i++) {
                names[i] = updated.get(i);
            }

            return true;
        }

        private void startGame() {
            hands.clear();
            trick.clear();
            history.clear();

            for (int i = 0; i < 4; i++) {
                hands.add(new ArrayList<>());
                scores[i] = 0;
            }

            List<Card> deck = new ArrayList<>();

            for (Suit suit : Suit.values()) {
                for (int rank = 2; rank <= 14; rank++) {
                    boolean removedTwo = rank == 2
                            && (suit == Suit.CLUBS
                            || suit == Suit.DIAMONDS);

                    if (!removedTwo) {
                        deck.add(new Card(suit, rank, 0));
                    }
                }
            }

            deck.add(new Card(null, 0, 1));
            deck.add(new Card(null, 0, 2));

            Collections.shuffle(deck);

            for (int i = 0; i < deck.size(); i++) {
                int player = (dealer + 1 + i) % 4;
                hands.get(player).add(deck.get(i));
            }

            Comparator<Card> order = Comparator
                    .comparingInt((Card card) ->
                            card.isJoker() ? 4 : card.suit().ordinal())
                    .thenComparingInt(card ->
                            card.isJoker() ? card.joker() : card.rank());

            hands.forEach(hand -> hand.sort(order));

            currentPlayer = (dealer + 1) % 4;
            leadSuit = null;
            trickComplete = false;
            gameFinished = false;

            showHandoff();
        }

        private void showHandoff() {
            VBox root = page();

            root.getChildren().addAll(
                    title("الدور الآن: " + names[currentPlayer]),
                    message("سلّم الجهاز إلى هذا اللاعب، ثم أظهر البطاقات."),
                    message("على بقية اللاعبين عدم النظر إلى الشاشة."),
                    button("إظهار بطاقاتي", this::renderGame)
            );

            show(root);
        }

        private void renderGame() {
            BorderPane root = new BorderPane();
            root.setPadding(new Insets(18));

            HBox toolbar = new HBox(
                    12,
                    button("لعبة جديدة", this::confirmNewGame),
                    button("تغيير الأسماء", this::editNames),
                    button("القواعد", this::showRules)
            );
            toolbar.setAlignment(Pos.CENTER);

            String phase;

            if (gameFinished) {
                phase = "انتهت اللعبة";
            } else if (trickComplete) {
                phase = "فاز بالجولة: " + names[lastWinner];
            } else {
                phase = "الدور الحالي: " + names[currentPlayer];
            }

            int roundNumber = trickComplete
                    ? history.size()
                    : history.size() + 1;

            Label status = message(
                    "الحكم: " + trump
                    + "   |   الموزّع: " + names[dealer]
                    + "   |   الجولة: " + roundNumber + " / 13"
                    + "\n" + phase
                    + (leadSuit == null
                    ? ""
                    : "   |   النوع المطلوب: " + leadSuit)
            );

            status.setStyle("""
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                    """);

            VBox top = new VBox(15, toolbar, status);
            top.setAlignment(Pos.CENTER);
            top.setPadding(new Insets(0, 0, 20, 0));
            root.setTop(top);

            FlowPane table = new FlowPane(18, 18);
            table.setAlignment(Pos.CENTER);
            table.setPadding(new Insets(25));
            table.setStyle("""
                    -fx-background-color: #145744;
                    -fx-background-radius: 18;
                    """);

            if (trick.isEmpty()) {
                Label empty = new Label("ابدأ الجولة بلعب بطاقة");
                empty.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                table.getChildren().add(empty);
            } else {
                for (Play play : trick) {
                    Label playerName = new Label(names[play.player()]);
                    playerName.setStyle("""
                            -fx-text-fill: white;
                            -fx-font-size: 18px;
                            """);

                    Label card = new Label(play.card().faceText());
                    card.setAlignment(Pos.CENTER);
                    card.setMinSize(105, 140);
                    card.setStyle(cardStyle(play.card()));

                    VBox played = new VBox(10, playerName, card);
                    played.setAlignment(Pos.CENTER);

                    table.getChildren().add(played);
                }
            }

            root.setCenter(table);

            VBox sidebar = new VBox(12);
            sidebar.setPrefWidth(245);
            sidebar.setPadding(new Insets(0, 0, 0, 18));
            sidebar.getChildren().add(new Label("النقاط"));

            for (int i = 0; i < 4; i++) {
                sidebar.getChildren().add(
                        new Label(names[i] + ": " + scores[i])
                );
            }

            sidebar.getChildren().add(new Label("تاريخ الجولات"));

            ListView<String> historyView = new ListView<>();
            historyView.setPrefHeight(300);

            for (int i = history.size() - 1; i >= 0; i--) {
                Completed completed = history.get(i);

                StringBuilder text = new StringBuilder(
                        "الجولة " + completed.number()
                        + " — " + names[completed.winner()]
                );

                for (Play play : completed.plays()) {
                    text.append("\n")
                            .append(names[play.player()])
                            .append(": ")
                            .append(play.card().shortText());
                }

                historyView.getItems().add(text.toString());
            }

            sidebar.getChildren().add(historyView);
            VBox.setVgrow(historyView, Priority.ALWAYS);
            root.setRight(sidebar);

            VBox bottom = new VBox(12);
            bottom.setPadding(new Insets(18, 0, 0, 0));
            bottom.setAlignment(Pos.CENTER);

            if (trickComplete) {
                bottom.getChildren().add(
                        button(
                                gameFinished
                                        ? "عرض النتيجة النهائية"
                                        : "الجولة التالية",
                                () -> {
                                    if (gameFinished) {
                                        showResults();
                                    } else {
                                        trick.clear();
                                        leadSuit = null;
                                        trickComplete = false;
                                        currentPlayer = lastWinner;
                                        showHandoff();
                                    }
                                }
                        )
                );
            } else {
                bottom.getChildren().add(
                        message("بطاقات " + names[currentPlayer]
                                + " — اضغط بطاقة للعبها")
                );

                FlowPane handPanel = new FlowPane(8, 8);
                handPanel.setAlignment(Pos.CENTER);

                int player = currentPlayer;

                for (Card card : hands.get(player)) {
                    Button cardButton = new Button(card.faceText());
                    cardButton.setMinSize(74, 100);
                    cardButton.setPrefSize(74, 100);
                    cardButton.setStyle(cardStyle(card));

                    boolean legal = isLegal(player, card);
                    cardButton.setDisable(!legal);

                    if (!legal) {
                        cardButton.setOpacity(0.38);
                    }

                    cardButton.setOnAction(event -> playCard(player, card));
                    handPanel.getChildren().add(cardButton);
                }

                bottom.getChildren().add(handPanel);
            }

            root.setBottom(bottom);
            show(root);
        }

        private String cardStyle(Card card) {
            return """
                    -fx-background-color: white;
                    -fx-background-radius: 9;
                    -fx-border-radius: 9;
                    -fx-border-color: #b3bcc5;
                    -fx-font-weight: bold;
                    -fx-font-size: 20px;
                    -fx-cursor: hand;
                    -fx-text-fill:
                    """ + (card.isRed() ? "#c62828;" : "#16212b;");
        }

        private boolean isLegal(int player, Card card) {
            if (gameFinished || trickComplete || player != currentPlayer) {
                return false;
            }

            if (!hands.get(player).contains(card)) {
                return false;
            }

            if (card.isJoker() || trick.isEmpty()) {
                return true;
            }

            boolean hasLeadSuit = hands.get(player).stream()
                    .anyMatch(c -> !c.isJoker() && c.suit() == leadSuit);

            return !hasLeadSuit || card.suit() == leadSuit;
        }

        private void playCard(int player, Card card) {
            if (!isLegal(player, card)) return;

            if (trick.isEmpty()) {
                if (card.isJoker()) {
                    ChoiceDialog<Suit> dialog = new ChoiceDialog<>(
                            trump, Suit.values()
                    );

                    dialog.initOwner(stage);
                    dialog.setTitle("بداية الجولة بجوكر");
                    dialog.setHeaderText("اختر النوع المطلوب اتباعه");
                    dialog.setContentText("النوع:");

                    Optional<Suit> chosen = dialog.showAndWait();
                    if (chosen.isEmpty()) return;

                    leadSuit = chosen.get();
                } else {
                    leadSuit = card.suit();
                }
            }

            hands.get(player).remove(card);
            trick.add(new Play(player, card));

            if (trick.size() == 4) {
                Play winner = trick.stream()
                        .max(Comparator.comparingInt(
                                play -> strength(play.card())))
                        .orElseThrow();

                lastWinner = winner.player();
                scores[lastWinner]++;

                history.add(new Completed(
                        history.size() + 1,
                        List.copyOf(trick),
                        lastWinner
                ));

                trickComplete = true;
                gameFinished = history.size() == 13;

                renderGame();
            } else {
                currentPlayer = (currentPlayer + 1) % 4;
                showHandoff();
            }
        }

        private int strength(Card card) {
            if (card.joker() == 2) return 1100;
            if (card.joker() == 1) return 1000;
            if (card.suit() == trump) return 200 + card.rank();
            if (card.suit() == leadSuit) return 100 + card.rank();
            return 0;
        }

        private void editNames() {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(stage);
            dialog.setTitle("تغيير أسماء اللاعبين");

            GridPane form = new GridPane();
            form.setHgap(12);
            form.setVgap(12);
            form.setPadding(new Insets(20));

            TextField[] fields = new TextField[4];

            for (int i = 0; i < 4; i++) {
                fields[i] = new TextField(names[i]);
                form.add(new Label("اللاعب " + (i + 1)), 0, i);
                form.add(fields[i], 1, i);
            }

            ButtonType save = new ButtonType(
                    "حفظ", ButtonBar.ButtonData.OK_DONE
            );

            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().getButtonTypes()
                    .addAll(save, ButtonType.CANCEL);

            Button saveButton = (Button) dialog.getDialogPane()
                    .lookupButton(save);

            saveButton.addEventFilter(
                    javafx.event.ActionEvent.ACTION,
                    event -> {
                        if (!saveNames(fields)) {
                            event.consume();
                        }
                    }
            );

            dialog.showAndWait().ifPresent(result -> {
                if (result == save) renderGame();
            });
        }

        private void showResults() {
            VBox root = page();
            root.getChildren().add(title("النتيجة النهائية"));

            int highest = 0;
            for (int score : scores) {
                highest = Math.max(highest, score);
            }

            List<String> winners = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                root.getChildren().add(
                        message(names[i] + ": " + scores[i] + " نقطة")
                );

                if (scores[i] == highest) {
                    winners.add(names[i]);
                }
            }

            root.getChildren().add(
                    title(
                            (winners.size() == 1 ? "الفائز: " : "تعادل: ")
                            + String.join("، ", winners)
                    )
            );

            root.getChildren().addAll(
                    button("لعبة جديدة", this::showSetup),
                    button("خروج", stage::close)
            );

            show(root);
        }

        private void confirmNewGame() {
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "ستفقد تقدم اللعبة الحالية. هل تريد المتابعة؟",
                    ButtonType.YES,
                    ButtonType.NO
            );

            alert.initOwner(stage);
            alert.setTitle("لعبة جديدة");
            alert.setHeaderText(null);

            if (alert.showAndWait().orElse(ButtonType.NO)
                    == ButtonType.YES) {
                showSetup();
            }
        }

        private void showRules() {
            info("قواعد الحكم والجوكر", """
                    • أربعة لاعبين، ولكل لاعب 13 بطاقة.
                    • تُحذف 2♣ و2♦ وتُضاف بطاقتا الجوكر.
                    • يبدأ اللاعب التالي للموزّع.
                    • يجب اتباع النوع الأول إن كان موجودًا في اليد.
                    • يجوز لعب الجوكر في أي وقت.
                    • عند البداية بجوكر يختار اللاعب النوع المطلوب.
                    • الأحمر أقوى من الأسود، ثم الحكم.
                    • إذا لم يوجد حكم أو جوكر يفوز أعلى ورق من النوع الأول.
                    • ترتيب البطاقات: A ثم K ثم Q ثم J ثم 10 حتى 2.
                    • الفائز يأخذ نقطة ويبدأ الجولة التالية.
                    • تنتهي اللعبة بعد 13 جولة، وقد يحدث تعادل.
                    • بين الأدوار تُخفى البطاقات لتسليم الجهاز.
                    """);
        }

        private void info(String heading, String text) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(stage);
            alert.setTitle(heading);
            alert.setHeaderText(null);
            alert.setContentText(text);
            alert.getDialogPane().setMinWidth(540);
            alert.showAndWait();
        }
    }
}
