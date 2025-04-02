import express from 'express';
import { fetchOptimalRoute } from '../controllers/optimalRouteController';

const router = express.Router();

router.post('/fetchOptimalRoute', fetchOptimalRoute);

export default router;