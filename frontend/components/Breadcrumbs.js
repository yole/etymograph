import Link from "next/link";
import {useEffect} from "react";

export default function Breadcrumbs(props) {
    useEffect(() => {
        document.title = "Etymograph : " + (props.langName !== undefined ? props.langName + " : " : "") +
            (props.steps !== undefined ? props.steps.map(s => s.title).join(": ") : "") +
            " : " + props.title
    }, []);

    return <h2>
        <small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            {props.langId !== undefined && <>
                <Link href={`/language/${props.langId}`}>{props.langName}</Link> {'> '}
            </>}
            {props.steps !== undefined && props.steps.map(s => <>
                <Link href={s.url}>{s.title}</Link> {'> '}
            </>)}
        </small>
        {props.title}
    </h2>
}
