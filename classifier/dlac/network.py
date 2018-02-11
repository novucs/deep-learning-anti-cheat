import numpy as np
from keras.layers import Dense
from keras.layers import LSTM
from keras.models import Sequential
from keras.preprocessing import sequence

# Protocol specification:
# ATTACK: [x, y, z, yaw, pitch]
# POSITION: [x, y, z]
# LOOK: [yaw, pitch]
# POSITION_LOOK: [x, y, z, yaw, pitch]
# TIME_DIFFERENCE: [time]
#
# [ax, ay, az, ayaw, apitch, px, py, pz, lyaw, lpitch, plx, ply, plz, plyaw, plpitch, time]
# Input size = 16


def unison_shuffled_copies(a, b):
    assert len(a) == len(b)
    p = np.random.permutation(len(a))
    return a[p], b[p]


# fix random seed for reproducibility
np.random.seed(7)

x_train = np.load("x.npy")
y_train = np.load("y.npy")
print(int((y_train == 1).sum()), (y_train == 0).sum())

x_train, y_train = unison_shuffled_copies(x_train, y_train)

max_history_length = 50
feature_count = 16
x_train = sequence.pad_sequences(x_train, maxlen=max_history_length, dtype="float32")
x_max = np.max(np.max(x_train, 0), 0)
x_min = np.min(np.min(x_train, 0), 0)
x_train = 2 * ((x_train - x_min) / (x_max - x_min)) - 1

[x_train, x_test] = np.array_split(x_train, 2)
[y_train, y_test] = np.array_split(y_train, 2)

model = Sequential()
model.add(Dense(16, input_shape=(max_history_length, feature_count)))
model.add(LSTM(100))
model.add(Dense(1, activation="sigmoid"))
model.compile(loss="binary_crossentropy", optimizer="adam", metrics=["accuracy"])

print(model.summary())

model.fit(x_train, y_train, epochs=10, batch_size=10)

scores = model.evaluate(x_test, y_test, verbose=0, batch_size=10)
print("Accuracy: %.2f%%" % (scores[1] * 100))
print(scores)

prediction = model.predict(x_test)
print(prediction)
print(y_test)
