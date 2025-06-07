import React, { Component } from 'react';
import './App.css';
import { BrowserRouter as Router, Route, Switch } from 'react-router-dom';
import Home from './components/Home';
import AuthorList from './components/AuthorList';
import AuthorEdit from "./components/AuthorEdit";

class App extends Component {
  render() {
    return (
        <Router>
          <Switch>
            <Route path='/' exact={true} component={Home}/>
            <Route path='/authors' exact={true} component={AuthorList}/>
            <Route path='/authors/:id' component={AuthorEdit}/>
          </Switch>
        </Router>
    )
  }
}

export default App;