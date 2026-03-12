# Loyalty App

A premium Android loyalty program and reward tracking application built for coffee shops and cafes. This app allows customers to register, build a profile, earn points via a backend-secured reward system, and browse a real-time menu.

## Features

- **Decoupled Backend Security**: Integration with Vercel Node.js backend using Retrofit to handle point manipulation safely off-device (e.g. Birthday rewards).
- **Google Firebase Integration**: Real-time integration with Firestore for tracking `menu_items`, generating live dynamic `home_banners`, and storing up-to-date user `profiles` securely.
- **MVVM Architecture**: Follows the Model-View-ViewModel design pattern utilizing `LiveData` observers and remote `Repository` files. 
- **Premium UI/UX**: Contains built-in validation formatting, Google Material Design 3 elements (like Chips, AppBars, Cards), and reactive bottom navigation.
- **Robust Testing Setup**: Setup to natively test ViewModels, APIs, and Data Models efficiently using Robolectric and Mockito.

## Tech Stack
- **OS**: Android (Java 8)
- **Architecture**: MVVM / Single-Activity Fragments
- **Database/Auth**: Firebase Firestore & Firebase Auth
- **Network**: Retrofit2 & OkHttp
- **Image Parsing**: Glide

## Project Structure
- `adapters/` - RecyclerView connectors orchestrating visual items like the menu or activities.
- `data/repository/` - Isolated API request & Firestore logic (i.e. `MenuRepository` or `UserRepository`).
- `fragments/` - MVVM UI Controllers (e.g., `HomeFragment`, `ProfileFragment`) executing strictly on observer models.
- `viewmodels/` - LiveData holding objects responding to the UI and speaking to the repositories.
- `models/` - Standard Java POJO mapped classes for database entities.

--incoming MVVM architecture


