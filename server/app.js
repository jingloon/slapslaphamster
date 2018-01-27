const express = require('express');
const app = require('express')();
const http = require('http').Server(app);
const io = require('socket.io')(http);

let tempSocket;

app.use(express.static('images'));

app.get('/', (req, res) => {
  res.sendFile(__dirname + '/index.html');
});

app.get('/update_score', (req, res) => {
  const score = req.query.score;
  tempSocket.emit('scoreEvent', {
    score: score
  });
  res.send({
    "success": true
  });
});

io.on('connection', (socket) => {
  console.log('a user is connected');

  tempSocket = socket;

  socket.on('disconnect', () => {
    console.log('a user is disconnected');
  });
});

http.listen(3000, () => {
  console.log('listening on *:3000');
});
