export interface Task {
    _id: string;
    name: string;
    start: string;
    end: string;        // latest time to reach a task
    duration: number;
    location_lat: number;
    location_lng: number;
    priority: number;
    description: string;
}
