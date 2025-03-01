# M4 - Requirements and Design

## 1. Change History

| Version | Date       | Description                                                                                                    | Author               |
|---------|------------|----------------------------------------------------------------------------------------------------------------|----------------------|
| 1.0     | 2025-01-31 | Initial Draft                                                                                                  | Team (4 Coordinates) |
| 2.0     | 2025-02-28 | MVP – Updated use case diagram to more effectively represent the actual use cases.                             | Team (4 Coordinates)                |

**Changed:**
- **Feb 26:** Updated functional requirement for route scheduling (*Find Optimal Route*), formatted success/failure scenarios, created a sequence diagram for route scheduling, and updated the dependency diagram format. *(David)*
- **Feb 27:** Adjusted main complexity to align with current implementation and added pseudo code for the brute force method. *(David)*
- **Feb 28:** Modified Functional Requirement 2 (Task Geofencing) to fit the structure of formal use cases. *(Amaan)*

## 2. Project Description

Managing daily tasks efficiently can be challenging, especially when dealing with multiple locations and deadlines. **GeoTask** is a smart task management app that leverages real-time location data, intelligent scheduling, and AI-powered recommendations to help users organize their tasks seamlessly.

### **Key Features:**
- **Location-Aware Task Management:** Tasks are mapped to specific locations, enhancing visualization and organization.
- **Smart Notifications:** Users receive timely reminders based on proximity, deadlines, and priority via real-time geofencing and scheduling algorithms.
- **Route and Schedule Optimization:** Automatically generates the most efficient task sequence and route based on the user's task list.

## 3. Requirements Specification

### **3.1. Use-Case Diagram**
![Use-Case Diagram](./images/Use-Case-Diagram-M4.png)
### **3.2. Actors Description**
1. **End User:** Logs in, manages tasks, and receives notifications based on proximity and prior task history.
2. **Google Maps API:** Handles location data, distance calculations, and navigation.
3. **Google Authentication API:** Manages secure user sign-in and integrates credentials with MongoDB for user identification and data storage.

### **3.3. Functional Requirements**

#### **3.3.1. User Login/Secure User Authentication**
- **Description:** Users log in via Google authentication and retrieve previously stored tasks.
- **Primary Actor:** End User
- **Success Scenarios:**
  - The user provides valid Google credentials and successfully logs in.
  - The system retrieves and displays the user's stored tasks.
- **Failure Scenarios:**
  - Incorrect login credentials result in authentication failure.
  - Google authentication service is unavailable.

#### **3.3.2. Task GeoFencing**
- **Description:** Sends push notifications when a task deadline is approaching or when the user enters a task’s designated area (geographical circles).
- **Primary Actor:** End User
- **Success Scenarios:**
  - The user adds a task with a defined geofencing area.
  - The system calculates and displays the geofence, triggering a notification when the user enters the area.
- **Failure Scenarios:**
  - The task’s geofence location is invalid (e.g., out of bounds).
  - Necessary location permissions are not enabled.

#### **3.3.3. Task Management System**
- **Description:** Create, modify, or delete a task with details such as deadline, location, priority level, frequency, and a short description.
- **Primary Actor:** End User
- **Success Scenarios:**
  - A new task is created with all required details.
  - An existing task is edited and saved.
  - A task is deleted, with the task list updating accordingly.
- **Failure Scenarios:**
  - Task creation fails due to missing or invalid input.
  - Task updates fail due to network or system errors.

#### **3.3.4. Intelligent Route Scheduling**
- **Description:** Generates an optimal route schedule using selected tasks based on start time, deadline, location, and estimated duration.
- **Primary Actor:** End User
- **Success Scenarios:**
  - The user selects tasks, and the system returns an optimized sequence with a total time cost estimate.
- **Failure Scenarios:**
  - The system fails to generate a route due to missing input (e.g., an empty task list or absent user location).
  - No viable task sequence is found under the current configuration.

### **3.4. Non-Functional Requirements**
1. **Scalability:** Must support at least **1,000 concurrent requests** with a response time under **200 milliseconds**.
2. **Real-Time Location Processing:** Must process location updates in the background with minimal battery consumption.
3. **Notification Accuracy:** Must deliver push notifications within **2 seconds** of detecting task deadlines or proximity triggers.
4. **Location Accuracy:** Must use location data with an accuracy of at least **10 meters**.

## 4. Designs Specification

### **4.1. Main Components**
1. **User**
   - **Description:** Handles user authentication, account management, and interaction with the Android UI. Users log in via Google Authentication and access their cloud-saved tasks.
   - **Purpose:** Ensures secure access and streamlined task management by linking user login data with task storage.
2. **Task**
   - **Description:** Enables creation, modification, and deletion of tasks, enhanced by real-time geofencing and dynamic scheduling.
   - **Purpose:** Boosts productivity by minimizing travel time and automating task scheduling based on location, priority, and deadlines.

### **4.2. Databases**
- **MongoDB**
  - **Description:** A NoSQL database.
  - **Purpose:** Store user-created tasks so that the user can access their tasks across multiple devices. This was chosen because of its simplicity compared to relational databases as well as the resiliency NoSQL can provide against network interruptions.

### **4.3. External Modules**
- **Google Maps API**
  - **Purpose:** Handles location data, calculates distances, and aids in navigation.
- **Google Authentication API**
  - **Purpose:** Manages secure user login and integrates credentials with task storage.

### **4.4. Frameworks**
1. **AWS**
   - Provides high availability with pre-configured tools and reduces infrastructure management overhead via AWS EC2.
2. **Docker**
   - Ensures consistent performance across different environments and reduces deployment times.

### **4.5. Dependencies Diagram**
![Dependencies Diagram](./images/dependencies-diagram-m4.png)
### **4.6. Functional Requirements Sequence Diagram**
Sign In:
![Sign In Sequence Diagram](./images/User_Login-REST.drawio.png)
Task Management:
![Task Management Sequence Diagram](./images/Task-Management-M4-sequence.png)
Find Least Travel Time Route:
![Find Least Travel Time Route](./images/Find-least-travel-time-sequence.png)
Task Geofencing:
![Task Geofencing](./images/task-geofencing-sequence.png)
### **4.7. Non-Functional Requirements Design**
1. **Scalability**: The system will be deployed on a cloud-based infrastructure with efficient logic and auto-scaling mechanisms to handle up to 1,000 concurrent requests. Efficient database indexing and caching techniques will be implemented to maintain response times under 2 seconds.
2. **Real-Time Location Processing**: The application will use optimized background location tracking with adaptive update intervals, leveraging Google Map API on Android.
3. **Notification Accuracy**: A real-time event-driven architecture with WebSockets will be used to ensure push notifications are delivered within 2 seconds. Task deadlines and proximity triggers will be preprocessed to reduce computation overhead during runtime.
4. **Location Accuracy**: The app will integrate GPS, Wi-Fi, and cellular network data to achieve at least 10-meter accuracy.

### **4.8. Main Project Complexity Design**
#### **Task Scheduling with Time Window Constraints**
- **Problem Definition**: Given a set of tasks (currently limited to within the same day) with location, start time, deadline, and estimated duration, the system must find the optimal order of execution to minimize travel time while ensuring all tasks are completed before their deadlines.
- **Challenges:**
    - The problem resembles the Traveling Salesman Problem (TSP) with Time Windows, which is NP-hard.
    - Tasks may have overlapping or conflicting deadlines, requiring dynamic adjustments.
    - Optional real-time traffic data will give a more accurate estimate, but comes at a much higher performance cost.
- **Proposed Solution:**
    - Bruteforce approach + Early stopping:
        - Start from the current user location and recursively explore all possible combinations. If at any point an unfinished task can no longer be reached before end time, it will be stopped early to reduce unnecessary computation.
        - Given the number tasks is small (generally less than 6), the computation cost is manageable.
        - Pseudo code:
![PseudoCode1](./images/pseudoCode1.png)
![PseudoCode2](./images/pseudoCode2.png)
![PseudoCode3](./images/pseudoCode3.png)