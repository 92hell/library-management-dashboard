import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import RequireRole from './components/RequireRole';
import AuthorEdit from './pages/AuthorEdit';
import AuthorList from './pages/AuthorList';
import BookDetail from './pages/BookDetail';
import BookEdit from './pages/BookEdit';
import BookList from './pages/BookList';
import BorrowedBookEdit from './pages/BorrowedBookEdit';
import BorrowedBookList from './pages/BorrowedBookList';
import Catalogue from './pages/Catalogue';
import FineList from './pages/FineList';
import Home from './pages/Home';
import Login from './pages/Login';
import MemberEdit from './pages/MemberEdit';
import MemberList from './pages/MemberList';
import MyLoans from './pages/MyLoans';
import Notifications from './pages/Notifications';
import Profile from './pages/Profile';
import Register from './pages/Register';
import Users from './pages/Users';

const Librarian = () => (
  <RequireRole role="LIBRARIAN">
    <Outlet />
  </RequireRole>
);
const Customer = () => (
  <RequireRole role="CUSTOMER">
    <Outlet />
  </RequireRole>
);

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        <Route path="profile" element={<Profile />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="catalogue/:id" element={<BookDetail />} />

        <Route element={<Customer />}>
          <Route path="catalogue" element={<Catalogue />} />
          <Route path="my/loans" element={<MyLoans />} />
        </Route>

        <Route element={<Librarian />}>
          <Route path="borrowed-books" element={<BorrowedBookList />} />
          <Route path="borrowed-books/:id" element={<BorrowedBookEdit />} />
          <Route path="books" element={<BookList />} />
          <Route path="books/:id" element={<BookEdit />} />
          <Route path="authors" element={<AuthorList />} />
          <Route path="authors/:id" element={<AuthorEdit />} />
          <Route path="members" element={<MemberList />} />
          <Route path="members/:id" element={<MemberEdit />} />
          <Route path="fines" element={<FineList />} />
          <Route path="users" element={<Users />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
