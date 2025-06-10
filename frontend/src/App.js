import React, {Component} from 'react';
import './App.css';
import {BrowserRouter as Router, Redirect, Route, Switch} from 'react-router-dom';
import Home from './components/Home';
import AuthorList from './components/AuthorList';
import AuthorEdit from "./components/AuthorEdit";
import MemberList from "./components/MemberList";
import MemberEdit from "./components/MemberEdit";
import BookList from "./components/BookList";
import BookEdit from "./components/BookEdit";
import BorrowedBookList from "./components/BorrowedBookList";
import BorrowedBookEdit from "./components/BorrowedBookEdit";

class App extends Component {
    render() {
        return (
            <Router>
                <Switch>
                    <Route path='/' exact={true} component={Home}/>

                    <Route path='/authors' exact={true} component={AuthorList}/>
                    <Route path='/authors/:id' component={AuthorEdit}/>

                    <Route path='/members' exact={true} component={MemberList}/>
                    <Route path='/members/:id' component={MemberEdit}/>

                    <Route path='/books' exact={true} component={BookList}/>
                    <Route path='/books/:id' component={BookEdit}/>

                    <Route path='/borrowed-books' exact={true} component={BorrowedBookList}/>
                    <Route path='/borrowed-books/:id' component={BorrowedBookEdit}/>

                    <Redirect to="/"/>
                </Switch>
            </Router>
        )
    }
}

export default App;