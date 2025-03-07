import request from 'supertest';
import express, { Request, Response } from 'express';
import * as dbService from '../../database/mongodb-ts/userService';

// Mock the database connection
jest.mock('../../database/mongodb-ts/database', () => ({
  connectDB: jest.fn().mockReturnValue({
    collection: jest.fn().mockReturnValue({
      insertOne: jest.fn().mockResolvedValue({ insertedId: 'mockedId' }),
      findOne: jest.fn().mockResolvedValue(null)
    })
  }),
  client: {
    db: jest.fn().mockReturnValue({
      collection: jest.fn().mockReturnValue({
        insertOne: jest.fn().mockResolvedValue({ insertedId: 'mockedId' }),
        findOne: jest.fn().mockResolvedValue(null)
      })
    })
  }
}));

// Create a new express app instance for testing
const app = express();
app.use(express.json());

// Define the /login route handler
app.post('/login', async (req: Request, res: Response) => {
  try {
    const { u_id, name, email } = req.body;
    if (!u_id || !name || !email) {
      throw new Error("Missing required field(s): u_id, name, and email must all be provided.");
    }
    
    const [newUserId, isNew] = await dbService.addUser(u_id, name, email);
    console.log(`[Logged in] User: ${name} (${email}) - ID: ${newUserId}`);
    res.status(200).json({ new_user_id: newUserId, is_new: isNew });
  } catch (error: any) {
    console.error(error);
    res.status(500).json({ error: error.message });
  }
});

describe("POST /login Endpoint", () => {
  describe("Unmocked Behavior", () => {
    test("Valid user data returns 200 and user details", async () => {
      const userData = {
        u_id: "test123",
        name: "Test User",
        email: "test@example.com"
      };
  
      const response = await request(app).post('/login').send(userData);
      
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('new_user_id');
      expect(response.body).toHaveProperty('is_new');
    });
  
    test("Missing required fields returns 500 with error message", async () => {
      const userData = {
        name: "Test User",
        email: "test@example.com"
        // u_id is missing
      };
  
      const response = await request(app).post('/login').send(userData);
      
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
    });
  });
  
  describe("Mocked Behavior", () => {
    test("Simulated database error returns 500 with proper error message", async () => {
      const spyAddUser = jest.spyOn(dbService, 'addUser')
        .mockImplementation(() => { throw new Error('Database connection failed'); });
  
      const userData = {
        u_id: "test123",
        name: "Test User",
        email: "test@example.com"
      };
  
      const response = await request(app).post('/login').send(userData);
  
      expect(response.status).toBe(500);
      expect(response.body.error).toBe('Database connection failed');
  
      spyAddUser.mockRestore();
    });
  
    test("Successful user creation returns mocked data", async () => {
      const spyAddUser = jest.spyOn(dbService, 'addUser')
        .mockResolvedValue(['mockedUserId123', 'true']);
  
      const userData = {
        u_id: "test123",
        name: "Test User",
        email: "test@example.com"
      };
  
      const response = await request(app).post('/login').send(userData);
  
      expect(response.status).toBe(200);
      expect(response.body).toEqual({
        new_user_id: 'mockedUserId123',
        is_new: 'true'
      });
  
      spyAddUser.mockRestore();
    });
  });
});
