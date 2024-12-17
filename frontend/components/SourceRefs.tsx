import { SourceRefViewModel } from "@/models";
import Link from "next/link";
import {useRouter} from "next/router";

interface SourceRefsProps {
    source: SourceRefViewModel[];
    span?: boolean;
}

export default function SourceRefs(props: SourceRefsProps) {
    const router = useRouter()
    const graph = router.query.graph

    const source = props.source
    if (source.length === 0) {
        return <></>
    }

    return <div className={props.span ? "source-span" : "source"}>{props.span ? " (source: " : "Source:"}{' '}
        {source.map((s, index) => {
            const prefix = index > 0 ? ", " : ""
            if (s.pubId !== null) {
                return <>{prefix}<Link href={`/${graph}/publication/${s.pubId}`}>{s.pubRefId}</Link>{":" + s.refText}</>
            }
            else if (s.refText.startsWith("http")) {
                return <>{prefix}<a href={s.refText}>{s.refText}</a></>
            }
            else {
                return prefix + s.refText
            }
        })}
        {props.span && ")"}
    </div>
}
