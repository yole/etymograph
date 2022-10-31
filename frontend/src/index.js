import React from 'react';
import ReactDOM from 'react-dom/client';
import {
    createBrowserRouter,
    RouterProvider,
    Route,
} from "react-router-dom";
import './index.css';
import CorpusIndex, { loader as corpusIndexLoader } from './routes/CorpusIndex'
import CorpusLangIndex, { loader as corpusLangIndexLoader } from "./routes/CorpusLangIndex";
import CorpusText, { loader as corpusTextLoader } from "./routes/CorpusText";
import Word, { loader as wordLoader } from "./routes/Word";
import Dictionary, { loader as dictionaryLoader } from "./routes/Dictionary";
import RuleList, { loader as ruleListLoader } from "./routes/RuleList";
import Rule, { loader as ruleLoader } from "./routes/Rule";
import CorpusTextEditor from "./routes/CorpusTextEditor";
import RuleEditor from "./routes/RuleEditor";

const router = createBrowserRouter([
    {
        path: '/',
        element: <CorpusIndex/>,
        loader: corpusIndexLoader
    },
    {
        path: '/corpus/:langId',
        element: <CorpusLangIndex/>,
        loader: corpusLangIndexLoader
    },
    {
        path: '/corpus/text/:id',
        element: <CorpusText/>,
        loader: corpusTextLoader
    },
    {
        path: '/corpus/new',
        element: <CorpusTextEditor/>
    },
    {
        path: '/word/:lang/*',
        element: <Word/>,
        loader: wordLoader
    },
    {
        path: '/dictionary/:lang',
        element: <Dictionary/>,
        loader: dictionaryLoader
    },
    {
        path: '/rules',
        element: <RuleList/>,
        loader: ruleListLoader
    },
    {
        path: '/rule/:id',
        element: <Rule/>,
        loader: ruleLoader
    },
    {
        path: '/rules/new',
        element: <RuleEditor/>
    }
])

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <RouterProvider router={router}/>
  </React.StrictMode>
);
