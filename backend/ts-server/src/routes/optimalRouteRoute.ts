import express from 'express';
import { fetchOptimalRoute } from '../controllers/optimalRouteController';

const router = express.Router();

router.get('/fetchOptimalRoute', fetchOptimalRoute);

export default router;