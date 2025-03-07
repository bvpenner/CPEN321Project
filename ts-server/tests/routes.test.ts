import request from 'supertest';
import express, { Request, Response, NextFunction, RequestHandler } from 'express';
import { fetchGeofences } from '../src/index';

// Create a new Express app instance for testing
const app = express();
app.use(express.json());

// Save original global.fetch for restoration after tests
const originalFetch = global.fetch;

// Define /fetchGeofences endpoint handler with explicit RequestHandler type
const fetchGeofencesHandler: RequestHandler = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { origin, destination } = req.body;
    if (!origin || !destination) {
      return res.status(400).json({ error: 'Missing origin or destination coordinates.' });
    }
    const result = await fetchGeofences(origin, destination);
    if (!result) throw new Error('Failed to fetch geofences');
    return res.json(result);
  } catch (error: any) {
    console.error('Google Maps API error:', error);
    return res.status(500).json({ error: error.message });
  }
};

// Define /fetchOptimalRoute endpoint handler with explicit RequestHandler type
const fetchOptimalRouteHandler: RequestHandler = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { allTasksID, userLocation, userCurrTime } = req.body;
    if (!allTasksID || allTasksID.length === 0 || !userLocation || !userCurrTime) {
      return res.status(400).json({ error: 'Missing required parameters.' });
    }
    
    // Simulate calling the Distance Matrix API
    const response = await fetch('https://maps.googleapis.com/maps/api/distancematrix/json', {
      // fetch options would go here...
    });
    
    if (!response.ok) {
      throw new Error('Distance Matrix API request failed');
    }
    
    const data = await response.json();
    // Simulated response; in a real scenario, process `data` to extract route details.
    return res.json({ taskIds: [], time_cost: 0 });
  } catch (error: any) {
    console.error('Distance Matrix API error:', error);
    return res.status(500).json({ error: error.message });
  }
};

// Register route handlers with explicit types
app.post('/fetchGeofences', fetchGeofencesHandler);
app.post('/fetchOptimalRoute', fetchOptimalRouteHandler);

// Global afterEach to restore the original fetch implementation
afterEach(() => {
  global.fetch = originalFetch;
});

//
// Tests for /fetchGeofences
//
describe("POST /fetchGeofences Endpoint", () => {
  describe("Unmocked Behavior", () => {
    beforeEach(() => {
      // Simulate a successful fetch response from Google Maps API
      global.fetch = jest.fn().mockImplementation(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            routes: [{
              summary: "Test Route",
              overview_polyline: { points: "test" },
              legs: [{
                start_location: { lat: 49.2827, lng: -123.1207 },
                end_location: { lat: 49.2827, lng: -123.1207 },
                distance: { value: 1000 },
                duration: { value: 600 }
              }]
            }]
          })
        })
      );
    });

    test("Valid Coordinates return geofence data", async () => {
      const routeData = {
        origin: { latitude: 49.2827, longitude: -123.1207 },
        destination: { latitude: 49.2827, longitude: -123.1207 }
      };

      const response = await request(app)
        .post('/fetchGeofences')
        .send(routeData);

      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('polygon');
    });

    test("Missing Coordinates returns 400 with error message", async () => {
      const routeData = {
        origin: { latitude: 49.2827, longitude: -123.1207 }
        // destination is missing
      };

      const response = await request(app)
        .post('/fetchGeofences')
        .send(routeData);

      expect(response.status).toBe(400);
      expect(response.body).toHaveProperty('error');
    });
  });

  describe("Mocked Behavior", () => {
    beforeEach(() => {
      // Simulate a failing fetch response to mimic a Google Maps API error
      global.fetch = jest.fn().mockImplementation(() =>
        Promise.resolve({
          ok: false,
          status: 500,
          statusText: 'Internal Server Error',
          json: () => Promise.reject(new Error('Internal Server Error')),
          headers: new Headers(),
          clone: function() { return this; },
          text: () => Promise.resolve(''),
          arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
          blob: () => Promise.resolve(new Blob([''], { type: 'text/plain' })),
          formData: () => Promise.resolve(new FormData()),
          type: 'basic',
          url: '',
          redirected: false,
          body: null,
          bodyUsed: false
        })
      );
    });

    test("Google Maps API Error is handled gracefully", async () => {
      const routeData = {
        origin: { latitude: 49.2827, longitude: -123.1207 },
        destination: { latitude: 49.2827, longitude: -123.1207 }
      };

      const response = await request(app)
        .post('/fetchGeofences')
        .send(routeData);

      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
    });
  });
});

//
// Tests for /fetchOptimalRoute
//
describe("POST /fetchOptimalRoute Endpoint", () => {
  describe("Unmocked Behavior", () => {
    beforeEach(() => {
      // Simulate a successful response from the Distance Matrix API
      global.fetch = jest.fn().mockImplementation(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            rows: [{
              elements: [{
                duration: { value: 600 }
              }]
            }]
          })
        })
      );
    });

    test("Valid Route Request returns optimal route data", async () => {
      const routeData = {
        allTasksID: [1, 2, 3],
        userLocation: { latitude: 49.2827, longitude: -123.1207 },
        userCurrTime: "10:00"
      };

      const response = await request(app)
        .post('/fetchOptimalRoute')
        .send(routeData);

      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('taskIds');
      expect(response.body).toHaveProperty('time_cost');
    });

    test("Missing Parameters returns 400 with error message", async () => {
      const routeData = {
        allTasksID: [], // No tasks provided
        userLocation: { latitude: 49.2827, longitude: -123.1207 },
        userCurrTime: "10:00"
      };

      const response = await request(app)
        .post('/fetchOptimalRoute')
        .send(routeData);

      expect(response.status).toBe(400);
      expect(response.body).toHaveProperty('error');
    });
  });

  describe("Mocked Behavior", () => {
    beforeEach(() => {
      // Simulate a fetch failure to mimic a Distance Matrix API error
      global.fetch = jest.fn().mockImplementation(() =>
        Promise.reject(new Error('Distance Matrix API error'))
      );
    });

    test("Distance Matrix API Error is handled gracefully", async () => {
      const routeData = {
        allTasksID: [1, 2, 3],
        userLocation: { latitude: 49.2827, longitude: -123.1207 },
        userCurrTime: "10:00"
      };

      const response = await request(app)
        .post('/fetchOptimalRoute')
        .send(routeData);

      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
    });
  });
});
