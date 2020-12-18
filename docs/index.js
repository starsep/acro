var map = new L.Map('map', {center: new L.LatLng(52.237049, 21.017532), zoom: 12});

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

function getJSON(url, callback) {
  var xhr = new XMLHttpRequest();
  xhr.open('GET', url, true);
  xhr.responseType = 'json';
  xhr.onload = function() {
    var status = xhr.status;
    if (status === 200) {
      callback(null, xhr.response);
    } else {
      callback(status, xhr.response);
    }
  };
  xhr.send();
}

function showPoints(points) {
  let redIcon = L.icon({
    iconUrl: 'leaf-red.png',
    shadowUrl: 'leaf-shadow.png',

    iconSize:     [38, 95], // size of the icon
    shadowSize:   [50, 64], // size of the shadow
    iconAnchor:   [22, 94], // point of the icon which will correspond to marker's location
    shadowAnchor: [4, 62],  // the same for the shadow
    popupAnchor:  [-3, -76] // point from which the popup should open relative to the iconAnchor
  });

  for (let i = 0; i < points.length; i++) {
    let point = points[i];
    L.marker([point.lat, point.lng], {icon: redIcon}).addTo(map);
  }
}

getJSON('bikes.json',
  function(err, data) {
    if (err !== null) {
      alert("Error: " + err);
    } else {
      let bikes = data.bikes;
      for (let i = 0; i < bikes.length; i++) {
        let bike = bikes[i];
        let marker = L.marker([bike.lat, bike.lng]).addTo(map);
      }
      // showPoints(data.points);
    }
  }
);

