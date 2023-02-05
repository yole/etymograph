import React from 'react';
import ReactDOM from 'react-dom/client';
import {
    createBrowserRouter,
    RouterProvider
} from "react-router-dom";
import './index.css';
import CorpusIndex, { loader as corpusIndexLoader } from './routes/CorpusIndex'
import LanguageIndex, { loader as languageIndexLoader } from './routes/LanguageIndex'
import CorpusLangIndex, { loader as corpusLangIndexLoader } from "./routes/CorpusLangIndex";
import CorpusText, { loader as corpusTextLoader } from "./routes/CorpusText";
import Word, {loader as wordLoader, WordError} from "./routes/Word";
import Dictionary, {compoundLoader, loader as dictionaryLoader} from "./routes/Dictionary";
import RuleList, { loader as ruleListLoader } from "./routes/RuleList";
import Rule, { loader as ruleLoader } from "./routes/Rule";
import CorpusTextEditor from "./routes/CorpusTextEditor";
import RuleEditor from "./routes/RuleEditor";
import ParadigmList, { loader as paradigmListLoader } from "./routes/ParadigmList";
import ParadigmEditor from "./routes/ParadigmEditor";
import Paradigm, { loader as paradigmLoader } from "./routes/Paradigm";
import WordParadigms, { loader as wordParadigmLoader } from "./routes/WordParadigms";

const router = createBrowserRouter([
    {
        path: '/',
        element: <CorpusIndex/>,
        loader: corpusIndexLoader
    },
    {
        path: '/language/:langId',
        element: <LanguageIndex/>,
        loader: languageIndexLoader
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
        path: '/corpus/:lang/new',
        element: <CorpusTextEditor/>
    },
    {
        path: '/word/:lang/*',
        element: <Word/>,
        loader: wordLoader,
        errorElement: <WordError/>
    },
    {
        path: '/dictionary/:lang',
        element: <Dictionary/>,
        loader: dictionaryLoader
    },
    {
        path: '/dictionary/:lang/compounds',
        element: <Dictionary/>,
        loader: compoundLoader
    },
    {
        path: '/rules/:langId',
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
    },
    {
        path: '/paradigms/:lang',
        element: <ParadigmList/>,
        loader: paradigmListLoader
    },
    {
        path: '/paradigm/:id',
        element: <Paradigm/>,
        loader: paradigmLoader
    },
    {
        path: '/paradigms/:lang/new',
        element: <ParadigmEditor/>
    },
    {
        path: '/word/:lang/:id/paradigms',
        element: <WordParadigms/>,
        loader: wordParadigmLoader
    }
])

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <RouterProvider router={router}/>
  </React.StrictMode>
);
